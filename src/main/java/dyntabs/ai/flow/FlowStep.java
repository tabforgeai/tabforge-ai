package dyntabs.ai.flow;

/**
 * One named step in an {@code EasyAI.flow()} pipeline: a single unit of work that reads the
 * shared {@link FlowContext} and produces a result.
 *
 * <p>A step is just a function {@code FlowContext -> result}. What it does inside is entirely
 * yours: call a plain Java service ({@code inventory.checkStock(...)}), or — only where you
 * genuinely need language — call the LLM ({@code EasyAI.extract(...).from(ctx.inputText())} or
 * {@code EasyAI.chat().build().send(...)}). The flow itself never calls a model; it only runs the
 * steps you wrote, in the order you wrote them. That is the whole point: the <em>order</em> is
 * your deterministic Java, the model is invited in only at the edges you declare.</p>
 *
 * <p><b>Familiar analogy:</b> a single station on an assembly line. The job folder
 * ({@link FlowContext}) slides in with everything the previous stations clipped into it; this
 * station reads what it needs, does its one job, and clips its own result back into the folder
 * before it slides on. A station does not decide the order of the line — that was fixed when the
 * line was laid out.</p>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   EasyAI.flow()
 *        → FlowBuilder.step("checkStock", ctx -&gt; ...)   // you register a FlowStep here
 *        → FlowBuilder.build()  → Flow
 *        → Flow.run(input)      → invokes each FlowStep.run(ctx) in registration order
 * </pre>
 *
 * <p>Because a step is a pure function of its {@link FlowContext}, it is trivially unit-testable:
 * hand it a {@code FlowContext} carrying a canned prior result (a mocked LLM output, say) and
 * assert on what it returns — no live model, no HTTP.</p>
 *
 * @param <T> the type of value this step produces and clips into the context under its step name
 * @see FlowContext
 * @see dyntabs.ai.FlowBuilder
 * @see dyntabs.ai.Flow
 */
@FunctionalInterface
public interface FlowStep<T> {

    /**
     * Runs this step against the flow's shared context.
     *
     * <p>Called by {@link dyntabs.ai.Flow#run(Object)} once, at this step's position in the
     * pipeline. The {@link FlowContext} passed in is an immutable snapshot holding the flow's
     * original input plus the results of every step that already ran, keyed by their step names
     * (read them with {@link FlowContext#get(String, Class)}). The value returned here is stored
     * under this step's name and becomes visible to every later step (and to
     * {@link FlowContext#result()} / {@link FlowContext#trail()} at the end).</p>
     *
     * <p>Checked exceptions are allowed so a step can call service methods that throw without
     * ceremony; {@link dyntabs.ai.Flow#run(Object)} wraps any thrown exception in a
     * {@link FlowException} tagged with this step's name and stops the pipeline.</p>
     *
     * @param ctx the shared, immutable-snapshot context for this run (never {@code null})
     * @return this step's result, to be stored under the step's name for downstream steps
     * @throws Exception if the step fails; the flow wraps it in {@link FlowException} and aborts
     */
    T run(FlowContext ctx) throws Exception;
}
