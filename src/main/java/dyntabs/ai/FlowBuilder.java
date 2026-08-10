package dyntabs.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIListener;
import dyntabs.ai.event.EventEmitter;
import dyntabs.ai.flow.FlowContext;
import dyntabs.ai.flow.FlowStep;

/**
 * Builder for an {@code EasyAI.flow()} pipeline: you lay out the named steps in the order they must
 * run, then {@link #build()} returns a ready-to-run {@link Flow}.
 *
 * <p><b>Familiar analogy:</b> writing a recipe card, or laying out the stations of an assembly line
 * in order. Each {@link #step(String, FlowStep)} call adds the next station; nothing runs until you
 * {@link #build()} the line and {@link Flow#run(Object)} it.</p>
 *
 * <pre>{@code
 * Flow flow = EasyAI.flow()
 *     .step("understand", ctx -> EasyAI.extract(OrderRequest.class).from(ctx.inputText()))
 *     .step("checkStock", ctx -> inventory.checkStock(ctx.get("understand", OrderRequest.class)))
 *     .step("pay",        ctx -> payment.charge(ctx.get("understand", OrderRequest.class)))
 *     .step("summarize",  ctx -> EasyAI.chat().build().send("Summarize:\n" + ctx.trail()))
 *     .withEventListener(e -> log.info("{}", e))
 *     .build();
 * }</pre>
 *
 * <h2>Place in the chain</h2>
 * <pre>
 *   EasyAI.flow()  → new FlowBuilder()
 *        → .step(name, fn) (repeated, order preserved) [.withEventListener(...)]
 *        → .build()  → Flow  → Flow.run(input)
 * </pre>
 *
 * @see EasyAI#flow()
 * @see Flow
 * @see FlowStep
 * @see dyntabs.ai.flow.FlowContext
 */
public final class FlowBuilder {

    private final List<Flow.NamedStep> steps = new ArrayList<>();
    private final Set<String> stepNames = new LinkedHashSet<>();
    /** The most recently added step, so {@link #orElse(FlowStep)} knows what to attach a fallback to. */
    private Flow.NamedStep lastStep;
    private EasyAIListener eventListener;

    FlowBuilder() {}

    /**
     * Appends a named step to the pipeline.
     *
     * <p>Steps run in the order you add them. The {@code name} is how later steps refer back to this
     * step's result ({@code ctx.get(name, Type.class)}), how it appears in {@link dyntabs.ai.flow.FlowContext#trail()},
     * and how a {@link dyntabs.ai.flow.FlowException} identifies it if it fails — so pick a short,
     * meaningful name ({@code "checkStock"}, {@code "pay"}). Names must be unique within a flow.</p>
     *
     * <p>Do whatever you need inside the step: call plain Java services, or reach for the LLM
     * ({@code EasyAI.extract(...)}, {@code EasyAI.chat()}) at the edges where language is the point.</p>
     *
     * @param name a short, unique, non-blank name for this step
     * @param step the work to run — a function of the shared {@link dyntabs.ai.flow.FlowContext}
     * @return this builder, for chaining
     * @throws IllegalArgumentException if {@code name} is blank, {@code step} is {@code null}, or the name is already used
     */
    public FlowBuilder step(String name, FlowStep<?> step) {
        addStep(name, step, null);
        return this;
    }

    /**
     * Appends a step that runs <b>only if</b> the given condition holds at that point in the flow;
     * otherwise the step is skipped entirely (it stores no result, so {@code ctx.has(name)} stays
     * {@code false} for later steps).
     *
     * <p>This makes a branch a first-class, named, skippable step — visible in
     * {@link dyntabs.ai.flow.FlowContext#trail()}, in the live event stream (a skipped step emits a
     * {@code STEP}/{@code WARNING} "skipped" row), and testable on its own. Use it when the branch is
     * a whole sub-path worth naming; for a trivial in-step choice a plain Java {@code if} inside a
     * normal {@link #step(String, FlowStep)} is still perfectly fine.</p>
     *
     * <p><b>Familiar analogy:</b> a station on the assembly line with a gate in front of it — the
     * folder only rolls in if the gate condition is met, otherwise it slides straight past.</p>
     *
     * <pre>{@code
     * .step("checkStock", ctx -> inventory.checkStock(ctx.get("understand", OrderRequest.class)))
     * .stepIf("reserve",
     *         ctx -> !ctx.get("checkStock", Boolean.class),               // only when NOT in stock
     *         ctx -> inventory.getFromWarehouse(ctx.get("understand", OrderRequest.class)))
     * }</pre>
     *
     * @param name      a short, unique, non-blank name for this step
     * @param condition the guard evaluated against the live {@link dyntabs.ai.flow.FlowContext} when the
     *                  flow reaches this step; the step runs only if it returns {@code true}
     * @param step      the work to run when the condition holds
     * @return this builder, for chaining
     * @throws IllegalArgumentException if {@code name} is blank/duplicate, or {@code condition}/{@code step} is null
     */
    public FlowBuilder stepIf(String name, Predicate<FlowContext> condition, FlowStep<?> step) {
        if (condition == null) {
            throw new IllegalArgumentException("Condition for step '" + name + "' must not be null.");
        }
        addStep(name, step, condition);
        return this;
    }

    /**
     * Attaches a fallback alternative to the <b>most recently added</b> step: if that step's primary
     * work throws, this alternative is tried instead (and further {@code orElse} calls chain more
     * alternatives, tried in order until one succeeds). If every attempt throws, the flow aborts with
     * a {@link dyntabs.ai.flow.FlowException} naming the step.
     *
     * <p>This is the deterministic, <em>declared</em> form of "if a step fails, try another way" — you
     * name the alternatives up front; nothing is discovered or searched. The alternative receives the
     * same {@link dyntabs.ai.flow.FlowContext} as the primary, and whichever attempt succeeds stores its
     * result under the step's name.</p>
     *
     * <p><b>Familiar analogy:</b> a backup machine at the same station — if the primary jams, the
     * folder is handed to the backup; only if all of them jam does the line stop.</p>
     *
     * <pre>{@code
     * .step("reserve", ctx -> inventory.reserveFromMain(ctx.get("understand", OrderRequest.class)))
     * .orElse(ctx -> inventory.reserveFromWarehouse(ctx.get("understand", OrderRequest.class), "WH-EU"))
     * .orElse(ctx -> inventory.backorder(ctx.get("understand", OrderRequest.class)))
     * }</pre>
     *
     * @param alternative the fallback work to try if the current step's primary (and any earlier
     *                    alternatives) throw
     * @return this builder, for chaining
     * @throws IllegalStateException    if no step has been added yet
     * @throws IllegalArgumentException if {@code alternative} is null
     */
    public FlowBuilder orElse(FlowStep<?> alternative) {
        if (lastStep == null) {
            throw new IllegalStateException("orElse(...) must follow a step(...) or stepIf(...) call.");
        }
        if (alternative == null) {
            throw new IllegalArgumentException(
                    "Fallback for step '" + lastStep.name + "' must not be null.");
        }
        lastStep.alternatives.add(alternative);
        return this;
    }

    /** Shared validation + registration for {@link #step} and {@link #stepIf}. */
    private void addStep(String name, FlowStep<?> step, Predicate<FlowContext> condition) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Step name must not be blank.");
        }
        if (step == null) {
            throw new IllegalArgumentException("Step '" + name + "' must not be null.");
        }
        if (!stepNames.add(name)) {
            throw new IllegalArgumentException(
                    "Duplicate step name '" + name + "'. Step names must be unique within a flow.");
        }
        Flow.NamedStep ns = new Flow.NamedStep(name, step, condition);
        steps.add(ns);
        lastStep = ns;
    }

    /**
     * Registers a transport-agnostic {@link EasyAIListener} that receives the flow's live
     * {@link dyntabs.ai.event.EasyAIEvent} stream ({@code STARTED} → {@code STEP_STARTED}/{@code STEP}
     * per step → {@code FINISHED}/{@code ERROR}).
     *
     * <p>This is the same observability hook {@code agent()} uses, so a flow run can feed a log, a
     * metric, or the TabForge demo's Activity panel with no extra plumbing. Optional — omit it and
     * the flow runs silently.</p>
     *
     * @param eventListener the listener to receive events, or {@code null} to disable emission
     * @return this builder, for chaining
     * @see dyntabs.ai.event.EasyAIEvent
     */
    public FlowBuilder withEventListener(EasyAIListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    /**
     * Builds the ready-to-run {@link Flow} from the steps registered so far.
     *
     * @return a new {@link Flow}
     * @throws IllegalStateException if no steps were added
     */
    public Flow build() {
        if (steps.isEmpty()) {
            throw new IllegalStateException(
                    "A flow needs at least one step; call step(name, fn) before build().");
        }
        EventEmitter emitter = new EventEmitter(Source.FLOW, eventListener);
        return new Flow(new ArrayList<>(steps), emitter);
    }
}
