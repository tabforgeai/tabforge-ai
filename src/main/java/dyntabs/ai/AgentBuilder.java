package dyntabs.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dyntabs.ai.agent.AgentStep;
import dyntabs.ai.agent.StepListener;
import dyntabs.ai.assistant.ToolIntrospector;
import dyntabs.ai.assistant.ToolMethod;

/**
 * Builder for creating an {@link EasyAgent} that autonomously plans and
 * executes multi-step tasks using your registered Java services.
 *
 * <h2>Basic usage</h2>
 * <pre>{@code
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
 * String result = agent.execute("Order 2 laptops, apply loyalty credit.");
 * }</pre>
 *
 * @see EasyAI#agent()
 * @see EasyAgent
 * @see dyntabs.ai.agent.StepListener
 * @see dyntabs.ai.agent.AgentStep
 */
public class AgentBuilder {

    static final int DEFAULT_MAX_STEPS = 10;

    static final String DEFAULT_PLANNER_PROMPT =
            "You are a planning agent. When given a complex task:\n" +
            "1. Think step by step and identify which tools you need to call and in what order.\n" +
            "2. Execute the steps sequentially.\n" +
            "3. Use the result of each step to inform the next decision.\n" +
            "4. If a step fails, adjust your plan and try an alternative approach.\n" +
            "5. Once you have all the information needed, provide a clear final answer.";

    private final List<Object> serviceObjects = new ArrayList<>();
    private int maxSteps = DEFAULT_MAX_STEPS;
    private boolean planningPrompt = false;
    private String customSystemMessage;
    private StepListener stepListener;
    private final EasyAIConfig.Builder configOverrides = EasyAIConfig.builder();
    private ChatModel externalModel;

    AgentBuilder() {}

    /**
     * Adds service objects whose public methods the agent can call.
     *
     * <p>Accepts plain POJOs and Jakarta EJB proxies ({@code @Stateless},
     * {@code @Stateful}, {@code @Singleton}) obtained via {@code @Inject}.
     * No annotations are needed on the service methods.</p>
     *
     * <pre>{@code
     * EasyAgent agent = EasyAI.agent()
     *     .withServices(inventoryService, paymentService, shippingService)
     *     .build();
     * }</pre>
     *
     * @param services one or more service objects (POJOs or injected EJB proxies)
     * @return this builder
     */
    public AgentBuilder withServices(Object... services) {
        for (Object s : services) {
            this.serviceObjects.add(s);
        }
        return this;
    }

    /**
     * Sets the maximum number of sequential service method calls the agent
     * is allowed to make before returning a response.
     *
     * <p><b>This parameter is strongly recommended.</b> Without a limit, a complex
     * task could trigger many sequential calls, significantly increasing token usage
     * and cost. Default is {@value #DEFAULT_MAX_STEPS} steps.</p>
     *
     * <pre>{@code
     * EasyAgent agent = EasyAI.agent()
     *     .withServices(...)
     *     .withMaxSteps(15)   // allow up to 15 tool calls per execute()
     *     .build();
     * }</pre>
     *
     * @param maxSteps maximum number of tool calls per task (must be &gt; 0)
     * @return this builder
     */
    public AgentBuilder withMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
        return this;
    }

    /**
     * Enables the built-in planning system message that instructs the LLM
     * to think step by step before calling tools.
     *
     * <p>When {@code true}, the agent receives a system message that says:
     * "Think step by step, plan which tools to call, execute sequentially,
     * adapt if a step fails."  This improves reliability for complex,
     * multi-step tasks with branching logic.</p>
     *
     * <p>If you also call {@link #withSystemMessage(String)}, the planning
     * prompt is prepended to your custom message.</p>
     *
     * <pre>{@code
     * EasyAgent agent = EasyAI.agent()
     *     .withServices(inventoryService, orderService)
     *     .withPlanningPrompt(true)
     *     .build();
     * }</pre>
     *
     * @param enable {@code true} to inject the built-in planning system message
     * @return this builder
     */
    public AgentBuilder withPlanningPrompt(boolean enable) {
        this.planningPrompt = enable;
        return this;
    }

    /**
     * Registers a listener that is called after each tool execution.
     *
     * <p>Use this to log or observe every step the agent takes.
     * The listener receives the step number, tool name, arguments, and result.</p>
     *
     * <pre>{@code
     * EasyAgent agent = EasyAI.agent()
     *     .withServices(orderService)
     *     .withStepListener(step ->
     *         log.info("[AGENT] Step {}: {}({}) -> {}",
     *             step.stepNumber(), step.toolName(),
     *             step.arguments(), step.result()))
     *     .build();
     * }</pre>
     *
     * @param stepListener the listener to call after each tool execution
     * @return this builder
     */
    public AgentBuilder withStepListener(StepListener stepListener) {
        this.stepListener = stepListener;
        return this;
    }

    /**
     * Sets a custom system message for the agent.
     *
     * <p>If {@link #withPlanningPrompt(boolean)} is also enabled,
     * the planning prompt is prepended to this message.</p>
     *
     * @param systemMessage the system message to set
     * @return this builder
     */
    public AgentBuilder withSystemMessage(String systemMessage) {
        this.customSystemMessage = systemMessage;
        return this;
    }

    /**
     * Overrides the model name for this agent.
     *
     * @param modelName the model name (e.g. {@code "gpt-4o"}, {@code "llama3"})
     * @return this builder
     */
    public AgentBuilder withModel(String modelName) {
        this.configOverrides.modelName(modelName);
        return this;
    }

    /**
     * Overrides the API key for this agent.
     *
     * @param apiKey the API key
     * @return this builder
     */
    public AgentBuilder withApiKey(String apiKey) {
        this.configOverrides.apiKey(apiKey);
        return this;
    }

    /**
     * Uses the given {@link ChatModel} directly, bypassing
     * {@code easyai.properties} and {@code EasyAI.configure()}.
     *
     * @param model the model to use
     * @return this builder
     */
    public AgentBuilder withChatModel(ChatModel model) {
        this.externalModel = model;
        return this;
    }

    /**
     * Builds and returns the {@link EasyAgent}.
     *
     * @return a ready-to-use agent
     */
    public EasyAgent build() {
        ChatModel model = externalModel != null
                ? externalModel
                : ModelFactory.create(effectiveConfig());

        AiServices<EasyAgent.AgentTask> serviceBuilder = AiServices
                .builder(EasyAgent.AgentTask.class)
                .chatModel(model);

        // Memory sized to accommodate a full multi-step conversation
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(maxSteps * 4);
        serviceBuilder.chatMemory(memory);

        // System message
        String effectiveSystemMessage = resolveSystemMessage();
        if (effectiveSystemMessage != null && !effectiveSystemMessage.isBlank()) {
            serviceBuilder.systemMessageProvider(id -> effectiveSystemMessage);
        }

        // Tool registration with step listener and structured error feedback
        if (!serviceObjects.isEmpty()) {
            AtomicInteger stepCounter = new AtomicInteger(0);
            List<ToolMethod> toolMethods = ToolIntrospector.introspect(serviceObjects.toArray());
            Map<ToolSpecification, ToolExecutor> toolMap = new HashMap<>();

            for (ToolMethod tm : toolMethods) {
                toolMap.put(tm.specification(), (toolExecutionRequest, memoryId) -> {
                    int step = stepCounter.incrementAndGet();

                    // Enforce max steps limit
                    if (step > maxSteps) {
                        return "MAX_STEPS_REACHED: You have reached the maximum of "
                                + maxSteps + " tool calls allowed for this task. "
                                + "Please provide a final answer based on the information gathered so far.";
                    }

                    String result;
                    try {
                        Object[] args = parseArguments(tm, toolExecutionRequest.arguments());
                        Object rawResult = tm.method().invoke(tm.targetObject(), args);
                        result = rawResult != null ? rawResult.toString() : "null";
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        String toolName = tm.specification().name();
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        result = "TOOL_ERROR | tool=" + toolName
                                + " | error=" + cause.getMessage()
                                + " | suggestion=Try a different approach or use an alternative tool";
                    } catch (Exception e) {
                        String toolName = tm.specification().name();
                        result = "TOOL_ERROR | tool=" + toolName
                                + " | error=" + e.getMessage()
                                + " | suggestion=Try a different approach or use an alternative tool";
                    }

                    if (stepListener != null) {
                        stepListener.onStep(new AgentStep(
                                step,
                                tm.specification().name(),
                                toolExecutionRequest.arguments(),
                                result));
                    }

                    return result;
                });
            }

            serviceBuilder.tools(toolMap);
        }

        return new EasyAgent(serviceBuilder.build());
    }

    private String resolveSystemMessage() {
        if (planningPrompt && customSystemMessage != null) {
            return DEFAULT_PLANNER_PROMPT + "\n\n" + customSystemMessage;
        }
        if (planningPrompt) {
            return DEFAULT_PLANNER_PROMPT;
        }
        return customSystemMessage;
    }

    private Object[] parseArguments(ToolMethod tm, String argumentsJson) {
        java.lang.reflect.Parameter[] params = tm.method().getParameters();
        if (params.length == 0) {
            return new Object[0];
        }
        try {
            com.google.gson.JsonObject jsonObj = com.google.gson.JsonParser
                    .parseString(argumentsJson).getAsJsonObject();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                com.google.gson.JsonElement element = jsonObj.get(paramName);
                args[i] = (element == null || element.isJsonNull())
                        ? getDefault(params[i].getType())
                        : convertJsonElement(element, params[i].getType());
            }
            return args;
        } catch (Exception e) {
            if (params.length == 1 && params[0].getType() == String.class) {
                return new Object[]{argumentsJson};
            }
            throw new RuntimeException("Failed to parse agent tool arguments: " + argumentsJson, e);
        }
    }

    private Object convertJsonElement(com.google.gson.JsonElement element, Class<?> type) {
        if (type == String.class)                          return element.getAsString();
        if (type == int.class || type == Integer.class)    return element.getAsInt();
        if (type == long.class || type == Long.class)      return element.getAsLong();
        if (type == double.class || type == Double.class)  return element.getAsDouble();
        if (type == float.class || type == Float.class)    return element.getAsFloat();
        if (type == boolean.class || type == Boolean.class) return element.getAsBoolean();
        return element.toString();
    }

    private Object getDefault(Class<?> type) {
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == double.class)  return 0.0;
        if (type == float.class)   return 0.0f;
        if (type == boolean.class) return false;
        return null;
    }

    private EasyAIConfig effectiveConfig() {
        EasyAIConfig base = EasyAI.getGlobalConfig();
        return EasyAIConfigLoader.applyOverrides(base, configOverrides.build());
    }
}
