package dyntabs.ai.flow;

/**
 * Thrown when a step in an {@code EasyAI.flow()} pipeline fails, naming exactly which step it was.
 *
 * <p>When a {@link FlowStep} throws, {@link dyntabs.ai.Flow#run(Object)} stops the pipeline and
 * wraps the original exception in a {@code FlowException} whose {@link #stepName()} tells you where
 * the line jammed. The original failure is preserved as the {@linkplain #getCause() cause}, so you
 * lose nothing.</p>
 *
 * <p><b>Familiar analogy:</b> a fault ticket on an assembly line that names the station that
 * jammed ("checkStock") and staples the machine's own error report to the back. You do not have to
 * guess which of six stations stopped the line — the ticket says so.</p>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   Flow.run(input)
 *        → FlowStep.run(ctx) throws  → wrapped as new FlowException(stepName, cause)
 *        → emitter.error(...)         → re-thrown to the caller of Flow.run
 * </pre>
 *
 * @see dyntabs.ai.Flow
 * @see FlowStep
 */
public class FlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The name of the step that failed. */
    private final String stepName;

    /**
     * Creates a flow failure tagged with the failing step's name.
     *
     * @param stepName the name of the step that threw (as registered with {@code FlowBuilder.step})
     * @param cause    the original exception the step threw
     */
    public FlowException(String stepName, Throwable cause) {
        super("Flow step '" + stepName + "' failed: "
                + (cause != null ? cause.getMessage() : "unknown error"), cause);
        this.stepName = stepName;
    }

    /**
     * Returns the name of the step that failed.
     *
     * <p>Lets a caller branch or report on <em>which</em> step broke without parsing the message —
     * e.g. {@code catch (FlowException e) { if ("pay".equals(e.stepName())) ... }}.</p>
     *
     * @return the failing step's name
     */
    public String stepName() {
        return stepName;
    }
}
