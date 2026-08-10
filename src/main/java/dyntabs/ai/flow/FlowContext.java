package dyntabs.ai.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The typed value-bag that travels through an {@code EasyAI.flow()} pipeline, carrying the flow's
 * original input and the result of every step that has run so far, each keyed by its step name.
 *
 * <p>This is the heart of the {@code flow()} idea: instead of the LLM holding the whole task in
 * its head and improvising, the <em>state</em> of the process lives in a plain, typed Java object
 * that you can read, assert on, and log. Each step reads what earlier steps produced
 * ({@link #get(String, Class)}), does its work, and its return value is clipped in under its own
 * name for the steps that follow.</p>
 *
 * <p><b>Familiar analogy:</b> the job folder that slides down an assembly line. It starts with the
 * order form ({@link #input()}), and each station reads the notes clipped in by earlier stations
 * and clips in its own before passing it on. At the end you hold the whole folder — every note in
 * the order it was added ({@link #trail()}), plus the last one ({@link #result()}).</p>
 *
 * <h2>Immutability &amp; testability</h2>
 * <p>A {@code FlowContext} is an immutable snapshot: {@link dyntabs.ai.Flow#run(Object)} builds a
 * fresh one before each step, holding the results accumulated up to that point. Steps never mutate
 * it — they return a value and the flow records it. This snapshot design is also what makes a step
 * testable in isolation: construct a {@code FlowContext} yourself with a canned prior result and
 * pass it straight into your {@link FlowStep} — no running flow required.</p>
 *
 * <pre>{@code
 * // Testing a step in isolation, no live LLM:
 * FlowContext ctx = new FlowContext(
 *         "order 3 blue watches",
 *         Map.of("understand", new OrderRequest("watch", 3, "blue")));
 * String txn = payStep.run(ctx);         // reads ctx.get("understand", OrderRequest.class)
 * assertEquals("txn-…", txn);
 * }</pre>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   Flow.run(input)
 *        → new FlowContext(input, results-so-far)   // rebuilt before each step
 *        → passed into FlowStep.run(ctx)            // the step reads input()/get(name, type)
 *        → (final snapshot returned to the caller of Flow.run)
 * </pre>
 *
 * @see FlowStep
 * @see dyntabs.ai.Flow
 * @see dyntabs.ai.FlowBuilder
 */
public final class FlowContext {

    private final Object input;
    private final Map<String, Object> results;

    /**
     * Builds an immutable context from the flow's input and the results gathered so far.
     *
     * <p>Called by {@link dyntabs.ai.Flow#run(Object)} before every step (with a growing result
     * map), and again at the end to produce the snapshot handed back to the caller. Application
     * code rarely constructs one directly — the main exception is unit tests, where building a
     * context with a canned prior result lets you exercise a single {@link FlowStep} without a
     * running flow.</p>
     *
     * @param input   the flow's original input (whatever was passed to {@code Flow.run(...)}); may be {@code null}
     * @param results prior step results keyed by step name; copied defensively, may be {@code null} or empty
     */
    public FlowContext(Object input, Map<String, Object> results) {
        this.input = input;
        this.results = Collections.unmodifiableMap(
                new LinkedHashMap<>(results == null ? Map.of() : results));
    }

    /**
     * Returns the flow's original input, untyped.
     *
     * <p>This is whatever you passed to {@code Flow.run(...)} — often the raw user text that the
     * first ("understand") step feeds to the LLM. Use {@link #input(Class)} when you want it typed.</p>
     *
     * @return the original input, or {@code null} if the flow was run with {@code null}
     */
    public Object input() {
        return input;
    }

    /**
     * Returns the flow's original input, cast to the requested type.
     *
     * @param <T>  the expected input type
     * @param type the class to cast the input to
     * @return the input as {@code T}
     * @throws ClassCastException if the input is not an instance of {@code type}
     */
    public <T> T input(Class<T> type) {
        return type.cast(input);
    }

    /**
     * Convenience for the common case where the input is (or should be read as) text — e.g. the
     * user's message the first LLM step needs to understand.
     *
     * @return the input rendered as a string, or {@code null} if the input is {@code null}
     */
    public String inputText() {
        return input == null ? null : input.toString();
    }

    /**
     * Returns the result an earlier step produced, cast to the requested type.
     *
     * <p>This is how a step reads what came before it — {@code ctx.get("understand",
     * OrderRequest.class)} in a {@code pay} step, for instance. The name is the exact step name
     * you registered with {@code FlowBuilder.step(name, ...)}.</p>
     *
     * @param <T>  the expected result type
     * @param name the step name whose result you want
     * @param type the class to cast that result to
     * @return the named step's result as {@code T}
     * @throws IllegalArgumentException if no step named {@code name} has run (yet)
     * @throws ClassCastException       if that step's result is not an instance of {@code type}
     */
    public <T> T get(String name, Class<T> type) {
        if (!results.containsKey(name)) {
            throw new IllegalArgumentException(
                    "No result named '" + name + "' in this flow context. Available so far: "
                            + results.keySet());
        }
        Object value = results.get(name);
        if (value != null && !type.isInstance(value)) {
            throw new ClassCastException(
                    "Result of step '" + name + "' is a " + value.getClass().getName()
                            + ", not a " + type.getName());
        }
        return type.cast(value);
    }

    /**
     * Tells whether a given step has already produced a result in this context.
     *
     * @param name the step name to check
     * @return {@code true} if a step named {@code name} has run and stored a result
     */
    public boolean has(String name) {
        return results.containsKey(name);
    }

    /**
     * Returns the most recently produced step result (the previous step's output when read
     * mid-flow, or the final step's output on the snapshot returned by {@code Flow.run}).
     *
     * @return the last stored result, or {@code null} if no step has produced one yet
     */
    public Object result() {
        Object last = null;
        for (Object v : results.values()) {
            last = v;
        }
        return last;
    }

    /**
     * Returns an unmodifiable, insertion-ordered view of every step result gathered so far,
     * keyed by step name.
     *
     * @return the step results in the order they were produced (never {@code null})
     */
    public Map<String, Object> results() {
        return results;
    }

    /**
     * Renders a compact, human-readable trace of what has happened so far — one line per completed
     * step, {@code name → value}, in order.
     *
     * <p>Handy for two things: feeding a final "summarize" LLM step
     * ({@code EasyAI.chat().build().send("Tell the user what happened:\n" + ctx.trail())}) and
     * plain logging/auditing of the run.</p>
     *
     * @return a newline-separated {@code name → value} trace (empty string if nothing has run yet)
     */
    public String trail() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : results.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(e.getKey()).append(" → ").append(String.valueOf(e.getValue()));
        }
        return sb.toString();
    }
}
