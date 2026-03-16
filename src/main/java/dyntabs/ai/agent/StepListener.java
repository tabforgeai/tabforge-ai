package dyntabs.ai.agent;

/**
 * Callback interface invoked after each tool call made by the agent.
 *
 * <p>Implement this to observe, log, or react to individual steps in the
 * agent's execution trace. The listener is called synchronously after
 * each service method returns (or fails), before the agent decides its next action.</p>
 *
 * <pre>{@code
 * EasyAgent agent = EasyAI.agent()
 *     .withServices(orderService, paymentService)
 *     .withMaxSteps(10)
 *     .withStepListener(step ->
 *         log.info("[AGENT] Step {}: {}({}) -> {}",
 *             step.stepNumber(), step.toolName(),
 *             step.arguments(), step.result()))
 *     .build();
 * }</pre>
 *
 * @see AgentStep
 * @see dyntabs.ai.AgentBuilder#withStepListener(StepListener)
 */
@FunctionalInterface
public interface StepListener {

    /**
     * Called after each tool execution during agent task processing.
     *
     * @param step the step that was just executed
     */
    void onStep(AgentStep step);
}
