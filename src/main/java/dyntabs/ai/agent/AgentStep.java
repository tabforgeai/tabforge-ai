package dyntabs.ai.agent;

/**
 * Represents a single tool call made by the agent during task execution.
 *
 * <p>Each time the agent calls one of your registered service methods,
 * an {@code AgentStep} is created and passed to the {@link StepListener}
 * (if one was registered via {@code AgentBuilder.withStepListener()}).</p>
 *
 * <pre>{@code
 * EasyAgent agent = EasyAI.agent()
 *     .withServices(orderService, inventoryService)
 *     .withStepListener(step ->
 *         log.info("[AGENT] Step {}: {}({}) -> {}",
 *             step.stepNumber(), step.toolName(),
 *             step.arguments(), step.result()))
 *     .build();
 * }</pre>
 *
 * @param stepNumber  the sequence number of this step, starting from 1
 * @param toolName    the name of the service method that was called (e.g. {@code "checkStock"})
 * @param arguments   the JSON string of arguments passed to the method
 * @param result      the string result returned by the method, or a structured error message
 *
 * @see StepListener
 * @see dyntabs.ai.AgentBuilder
 */
public record AgentStep(
        int stepNumber,
        String toolName,
        String arguments,
        String result
) {}
