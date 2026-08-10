package dyntabs.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.event.EasyAIEvent.Status;
import dyntabs.ai.event.EventEmitter;
import dyntabs.ai.flow.FlowContext;
import dyntabs.ai.flow.FlowException;
import dyntabs.ai.flow.FlowStep;

/**
 * A deterministic, developer-authored pipeline: it runs a fixed sequence of named steps, threading
 * a typed {@link FlowContext} from one to the next, and calls the LLM only where <em>you</em> put a
 * step that calls it.
 *
 * <h2>What this is, and how it differs from {@link EasyAgent}</h2>
 * <p>An {@link EasyAgent} hands the whole task to the model and lets it decide which service to
 * call next and in what order — great when the path is genuinely unknown, but non-deterministic and
 * hard to unit-test. A {@code Flow} is the opposite discipline: <b>you</b> author the order in
 * plain Java, and the model is invited in only at the edges you declare (typically "understand the
 * request" at the start and "summarize the outcome" at the end). For a known business process —
 * check stock, take payment, create order, ship — there is exactly one correct order and it must be
 * the same every time; a flow pins it down.</p>
 *
 * <p><b>Familiar analogy:</b> a recipe followed step by step versus a chef improvising. {@code
 * agent()} is the improvising chef (powerful, unpredictable); {@code flow()} is the recipe card —
 * the same dish, the same order, every service, testable bite by bite. The LLM is the one exotic
 * ingredient you reach for only at the two moments the recipe calls for it.</p>
 *
 * <pre>{@code
 * FlowContext out = EasyAI.flow()
 *     .step("understand", ctx -> EasyAI.extract(OrderRequest.class).from(ctx.inputText())) // LLM
 *     .step("checkStock", ctx -> inventory.checkStock(ctx.get("understand", OrderRequest.class)))
 *     .step("pay",        ctx -> payment.charge(ctx.get("understand", OrderRequest.class)))
 *     .step("createOrder",ctx -> orders.create(ctx.get("understand", OrderRequest.class)))
 *     .step("ship",       ctx -> shipping.schedule(ctx.get("createOrder", Order.class)))
 *     .step("summarize",  ctx -> EasyAI.chat().build()
 *                                    .send("Tell the user what happened:\n" + ctx.trail()))       // LLM
 *     .withEventListener(e -> log.info("{}", e))  // same live stream as agent() → Activity panel
 *     .build()
 *     .run("Order 3 blue watches, ship home.");
 *
 * String reply = (String) out.result();   // the "summarize" step's output
 * }</pre>
 *
 * <h2>What it buys you</h2>
 * <ol>
 *   <li><b>Correctness</b> — the money/state path runs in the same order every time.</li>
 *   <li><b>Testability</b> — mock the LLM at the edges and assert invariants ("stock is checked
 *       before pay"); each {@link FlowStep} is a pure function of its {@link FlowContext}.</li>
 *   <li><b>Safety</b> — a prompt-injection cannot reorder or invent steps; the model does not drive.</li>
 *   <li><b>Cost/latency</b> — two model calls at the edges, not six in a loop.</li>
 *   <li><b>Explainability</b> — the run is your code and {@link FlowContext#trail()}, not the model's
 *       monologue.</li>
 * </ol>
 *
 * <h2>Place in the chain</h2>
 * <pre>
 *   EasyAI.flow()  → FlowBuilder.step(...)·step(...)  → FlowBuilder.build()  → Flow
 *        → Flow.run(input)
 *             → for each step: new FlowContext(input, results-so-far) → FlowStep.run(ctx)
 *             → returns the final FlowContext snapshot
 * </pre>
 *
 * <p>Observability rides the same {@link dyntabs.ai.event.EasyAIEvent} stream every other EasyAI
 * capability uses (here under {@link dyntabs.ai.event.EasyAIEvent.Source#FLOW}), so the TabForge
 * demo's Activity panel renders a flow run with no new wiring.</p>
 *
 * <p>Steps run in registration order. Two declarative refinements keep the branch logic visible at
 * the flow level (in the trace, the event stream, and tests) instead of buried inside a step:
 * {@link FlowBuilder#stepIf(String, java.util.function.Predicate, FlowStep)} runs a step only when a
 * guard holds (otherwise it is skipped and stores no result), and
 * {@link FlowBuilder#orElse(FlowStep)} attaches fallback alternatives that are tried, in order, if
 * the primary step throws. Both are <em>declared</em>, not discovered — there is no planner; you
 * still author the path. (Simple in-step conditionals with a plain Java {@code if} remain perfectly
 * fine; {@code stepIf} is for when you want the branch to be a first-class, named, skippable step.)</p>
 *
 * @see EasyAI#flow()
 * @see FlowBuilder
 * @see FlowContext
 * @see FlowStep
 * @see EasyAgent
 */
public final class Flow {

    private static final Logger log = LoggerFactory.getLogger(Flow.class);

    /**
     * One registered step: its name, the primary work to run, an optional guard predicate
     * (for {@code stepIf}), and an ordered list of fallback alternatives (for {@code orElse}).
     * Library-internal.
     */
    static final class NamedStep {
        final String name;
        final FlowStep<?> step;
        /** Guard from {@code stepIf}; when non-null and false at run time, the step is skipped. */
        final Predicate<FlowContext> condition;
        /** Fallbacks from {@code orElse}, tried in order if the primary (and earlier ones) throw. */
        final List<FlowStep<?>> alternatives = new ArrayList<>();

        NamedStep(String name, FlowStep<?> step, Predicate<FlowContext> condition) {
            this.name = name;
            this.step = step;
            this.condition = condition;
        }
    }

    private final List<NamedStep> steps;

    /** Live event stream for this flow; never {@code null} (a no-op when no listener was set). */
    private final EventEmitter emitter;

    Flow(List<NamedStep> steps, EventEmitter emitter) {
        this.steps = steps;
        this.emitter = emitter;
    }

    /**
     * Runs every step once, in registration order, threading a typed {@link FlowContext} through
     * them, and returns the final context snapshot.
     *
     * <p>Called by application code (the end of the {@code EasyAI.flow()...build().run(input)}
     * chain). Before each step it builds a fresh {@link FlowContext} holding {@code input} plus the
     * results of all steps that have already run; it passes that to {@link FlowStep#run(FlowContext)}
     * and stores the returned value under the step's name for later steps. If a step throws, the
     * pipeline stops and the exception is wrapped in a {@link FlowException} naming that step.</p>
     *
     * <p>The run is bracketed with {@link dyntabs.ai.event.EasyAIEvent.Phase#STARTED} /
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#FINISHED} events, with a {@code STEP_STARTED} before
     * and a {@code STEP} after each step (and an {@code ERROR} on failure) — the same live stream
     * {@code agent()} emits, so any registered {@link dyntabs.ai.event.EasyAIListener} sees the run
     * unfold in real time.</p>
     *
     * @param input the flow's input, made available to every step via {@link FlowContext#input()}
     * @return the final {@link FlowContext} snapshot — read the outcome with
     *         {@link FlowContext#result()}, an intermediate value with
     *         {@link FlowContext#get(String, Class)}, or the whole run with {@link FlowContext#trail()}
     * @throws FlowException if any step throws; {@link FlowException#stepName()} names the failing step
     */
    public FlowContext run(Object input) {
        log.debug("Flow starting: {} step(s)", steps.size());
        emitter.started("Running flow");

        Map<String, Object> results = new LinkedHashMap<>();
        try {
            for (NamedStep ns : steps) {
                FlowContext ctx = new FlowContext(input, results);

                // stepIf guard: if the condition is present and false, skip the step entirely —
                // nothing is stored under its name (so has(name) stays false for later steps).
                if (ns.condition != null && !ns.condition.test(ctx)) {
                    emitter.step(ns.name, "skipped (condition not met)", Status.WARNING);
                    log.debug("Flow step '{}' skipped (condition false)", ns.name);
                    continue;
                }

                emitter.stepStarted(ns.name, null);
                log.trace("Flow step '{}' starting", ns.name);

                // Primary attempt, then each orElse alternative in order until one succeeds.
                List<FlowStep<?>> attempts = new ArrayList<>(1 + ns.alternatives.size());
                attempts.add(ns.step);
                attempts.addAll(ns.alternatives);

                Object value = null;
                boolean succeeded = false;
                Exception firstError = null;
                for (int i = 0; i < attempts.size(); i++) {
                    if (i > 0) {
                        emitter.retry(ns.name, "attempt " + (i + 1) + " (previous attempt failed)");
                        log.debug("Flow step '{}' falling back to alternative {}", ns.name, i);
                    }
                    try {
                        value = attempts.get(i).run(ctx);
                        succeeded = true;
                        break;
                    } catch (Exception e) {
                        if (firstError == null) {
                            firstError = e;
                        }
                    }
                }

                if (!succeeded) {
                    emitter.step(ns.name, "error: " + firstError.getMessage(), Status.ERROR);
                    throw new FlowException(ns.name, firstError);
                }

                results.put(ns.name, value);
                emitter.step(ns.name, String.valueOf(value), Status.SUCCESS);
                log.trace("Flow step '{}' done", ns.name);
            }

            emitter.finished("Flow complete");
            log.debug("Flow finished: {} step(s)", steps.size());
            return new FlowContext(input, results);
        } catch (FlowException e) {
            Throwable cause = e.getCause();
            emitter.error("Flow failed at step '" + e.stepName() + "'",
                    cause != null ? cause.getMessage() : e.getMessage());
            log.warn("Flow failed at step '{}'", e.stepName(), e);
            throw e;
        }
    }
}
