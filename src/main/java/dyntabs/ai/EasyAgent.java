package dyntabs.ai;

/**
 * An AI agent that autonomously plans and executes multi-step tasks
 * by orchestrating calls to your registered Java services.
 *
 * <p>Unlike a regular AI assistant (which answers a single question),
 * an {@code EasyAgent} receives a complex task and breaks it into steps,
 * calling your service methods in the right order, using the result of
 * each step to decide what to do next.</p>
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * // Your existing Jakarta EE services — no changes needed
 * @Stateless InventoryService inventoryService;
 * @Stateless PaymentService   paymentService;
 * @Stateless OrderService     orderService;
 *
 * EasyAgent agent = EasyAI.agent()
 *     .withServices(inventoryService, paymentService, orderService)
 *     .withMaxSteps(10)
 *     .withPlanningPrompt(true)
 *     .withStepListener(step ->
 *         log.info("[AGENT] Step {}: {}({}) -> {}",
 *             step.stepNumber(), step.toolName(),
 *             step.arguments(), step.result()))
 *     .build();
 *
 * String result = agent.execute(
 *     "Order 2 laptops for user U123, apply loyalty credit, " +
 *     "use fallback warehouse WH-EU if out of stock."
 * );
 * }</pre>
 *
 * <h2>What the agent does autonomously</h2>
 * <ol>
 *   <li>Receives the task description</li>
 *   <li>Plans which service methods to call and in what order</li>
 *   <li>Executes each step, passing results forward</li>
 *   <li>Adapts the plan if a step fails or returns unexpected data</li>
 *   <li>Returns a final natural-language summary when done</li>
 * </ol>
 *
 * <h2>Safety</h2>
 * <p>Always set {@code withMaxSteps()} to cap the number of tool calls.
 * Without a limit, a complex task could generate many sequential calls,
 * significantly increasing token usage and cost. The default is 10 steps.</p>
 *
 * <h2>Transactional note</h2>
 * <p>EasyAgent does not manage transactions across steps. If the agent calls
 * {@code payment.process()} and then fails on the next step, the payment
 * will not be rolled back. Design your services to be idempotent or provide
 * compensating transactions where needed.</p>
 *
 * @see EasyAI#agent()
 * @see AgentBuilder
 * @see dyntabs.ai.agent.StepListener
 * @see dyntabs.ai.agent.AgentStep
 */
public class EasyAgent {

    private final AgentTask agentTask;

    EasyAgent(AgentTask agentTask) {
        this.agentTask = agentTask;
    }

    /**
     * Internal interface proxied by LangChain4J AiServices.
     * Not exposed to callers — use {@link #execute(String)} instead.
     */
    interface AgentTask {
        String execute(String task);
    }

    /**
     * Executes the given task description.
     *
     * <p>The agent will autonomously call your registered services
     * in the correct order to complete the task, up to the configured
     * {@code maxSteps} limit.</p>
     *
     * <pre>{@code
     * String result = agent.execute(
     *     "Poruči 2 laptopa za korisnika U123, iskoristi loyalty kredit, " +
     *     "dostavi na Beograd. Ako nema na lageru, uzmi iz magacina WH-EU."
     * );
     * }</pre>
     *
     * @param task a natural-language description of the task to perform
     * @return a natural-language summary of what was done and the final outcome
     */
    public String execute(String task) {
        return agentTask.execute(task);
    }
}
