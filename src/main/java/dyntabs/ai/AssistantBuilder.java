package dyntabs.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dyntabs.ai.annotation.EasyAIAssistant;
import dyntabs.ai.annotation.EasyRAG;
import dyntabs.ai.assistant.ToolIntrospector;
import dyntabs.ai.assistant.ToolMethod;
import dyntabs.ai.rag.DocumentSource;
import dyntabs.ai.rag.RagEngine;

/**
 * Builder for creating AI assistant proxies from annotated interfaces.
 *
 * <p>This builder creates a runtime proxy that forwards method calls to an AI model.
 * You can optionally add tool objects (your existing Java services) that the AI can call,
 * and document sources (RAG) that the AI can reference.</p>
 *
 * <h3>Use Case 1: Simple AI Assistant (No Tools)</h3>
 * <pre>{@code
 * @EasyAIAssistant(systemMessage = "You are a helpful translator")
 * public interface Translator {
 *     String translate(String text);
 * }
 *
 * Translator t = EasyAI.assistant(Translator.class).build();
 * String result = t.translate("Hello, how are you?");
 * // result: "Bonjour, comment allez-vous?" (depending on system message)
 * }</pre>
 *
 * <h3>Use Case 2: AI Assistant with Tools (AI Calls Your Java Code)</h3>
 * <p>This is the most powerful feature. Pass your existing service objects to
 * {@link #withTools(Object...)}, and the AI will call their methods when needed.
 * <b>No annotations are required on your service classes.</b></p>
 * <pre>{@code
 * // Your existing service - plain Java, no AI annotations
 * public class WeatherService {
 *     public String getWeather(String city) {
 *         return weatherApi.fetch(city).toString();
 *     }
 * }
 *
 * @EasyAIAssistant(systemMessage = "You are a weather assistant")
 * public interface WeatherBot {
 *     String ask(String question);
 * }
 *
 * // Wire it together
 * WeatherBot bot = EasyAI.assistant(WeatherBot.class)
 *     .withTools(new WeatherService())
 *     .build();
 *
 * // AI automatically calls WeatherService.getWeather("London") behind the scenes
 * String answer = bot.ask("What's the weather like in London?");
 * }</pre>
 *
 * <h3>Use Case 3: Multiple Tool Services</h3>
 * <pre>{@code
 * SupportBot bot = EasyAI.assistant(SupportBot.class)
 *     .withTools(orderService, userService, inventoryService)
 *     .build();
 *
 * // AI can call methods from ANY of the three services
 * bot.ask("What is the status of order #123 for user john@example.com?");
 * }</pre>
 *
 * <h3>Use Case 4: Jakarta EJB Beans as Tools</h3>
 * <p>EJB beans ({@code @Stateless}, {@code @Stateful}, {@code @Singleton}) injected via
 * {@code @Inject} work as tools out of the box. EasyAI automatically detects the EJB proxy
 * and discovers business methods from the actual bean class. Method calls go through the
 * container proxy, so transactions, security, and interceptors work normally.</p>
 * <pre>{@code
 * @Stateless
 * public class OrderService {
 *     @PersistenceContext private EntityManager em;
 *
 *     public String findOrder(String orderId) {
 *         return em.find(Order.class, orderId).toString();
 *     }
 * }
 *
 * // In your CDI bean:
 * @Inject OrderService orderService;   // EJB proxy from the container
 *
 * SupportBot bot = EasyAI.assistant(SupportBot.class)
 *     .withTools(orderService)          // just pass the injected proxy
 *     .build();
 *
 * bot.ask("Where is order #123?");
 * // AI calls orderService.findOrder("123") through the EJB proxy
 * }</pre>
 *
 * <h3>Use Case 5: Override Settings Per-Assistant</h3>
 * <pre>{@code
 * Translator t = EasyAI.assistant(Translator.class)
 *     .withModel("gpt-4o")           // use a specific model
 *     .withMemory(50)                 // remember 50 messages
 *     .withSystemMessage("Translate everything to Serbian")
 *     .build();
 * }</pre>
 *
 * @param <T> the assistant interface type
 * @see EasyAI#assistant(Class)
 * @see dyntabs.ai.annotation.EasyAIAssistant
 * @see dyntabs.ai.annotation.EasyTool
 * @see dyntabs.ai.annotation.EasyRAG
 */
public class AssistantBuilder<T> {

    private final Class<T> assistantInterface;
    private final List<Object> toolObjects = new ArrayList<>();
    private int memorySize = 20;
    private String systemMessage;
    private final EasyAIConfig.Builder configOverrides = EasyAIConfig.builder();
    private ChatLanguageModel externalModel;

    // Programmatic RAG config (overrides @EasyRAG annotation if set)
    private String[] ragSources;
    private List<DocumentSource> ragDocumentSources;
    private int ragMaxResults = 3;
    private double ragMinScore = 0.5;

    AssistantBuilder(Class<T> assistantInterface) {
        this.assistantInterface = assistantInterface;

        // Read @EasyAIAssistant annotation for defaults
        EasyAIAssistant annotation = assistantInterface.getAnnotation(EasyAIAssistant.class);
        if (annotation != null && !annotation.systemMessage().isEmpty()) {
            this.systemMessage = annotation.systemMessage();
        }
    }

    /**
     * Adds tool objects whose public methods the AI can call.
     *
     * <p>Accepts plain POJOs and Jakarta EJB proxies ({@code @Stateless},
     * {@code @Stateful}, {@code @Singleton}) obtained via {@code @Inject}.
     * EJB proxies are detected automatically — business methods are discovered
     * from the actual bean class, while invocations go through the proxy
     * so that container services (transactions, security, interceptors) work normally.</p>
     *
     * @param tools one or more service objects (POJOs or injected EJB proxies)
     * @return this builder
     */
    public AssistantBuilder<T> withTools(Object... tools) {
        for (Object tool : tools) {
            this.toolObjects.add(tool);
        }
        return this;
    }

    public AssistantBuilder<T> withMemory(int maxMessages) {
        this.memorySize = maxMessages;
        return this;
    }

    public AssistantBuilder<T> withSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
        return this;
    }

    public AssistantBuilder<T> withModel(String modelName) {
        this.configOverrides.modelName(modelName);
        return this;
    }

    public AssistantBuilder<T> withApiKey(String apiKey) {
        this.configOverrides.apiKey(apiKey);
        return this;
    }

    /**
     * Enables RAG (document-powered AI) with the given document sources.
     *
     * <p>This is the programmatic alternative to the {@code @EasyRAG} annotation.
     * Use this when your document paths are not known at compile time, for example
     * when they come from a database, user upload, or application configuration.</p>
     *
     * <p><b>In a web application</b>, use {@code file:} prefix to point to documents
     * on the server's file system, or pass any absolute path:</p>
     *
     * <pre>{@code
     * // Documents on the server file system
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG("file:C:/app-data/docs/policy.pdf")
     *     .build();
     *
     * // Path from application config or database
     * String docPath = appConfig.getDocumentPath();
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG(docPath)
     *     .build();
     *
     * // Multiple documents
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG("file:/data/policy.pdf", "file:/data/faq.pdf")
     *     .build();
     * }</pre>
     *
     * <p>Supports the same path prefixes as {@code @EasyRAG}:
     * {@code classpath:}, {@code file:}, or plain relative paths.</p>
     *
     * @param sources one or more document paths
     * @return this builder
     * @see #withRAG(String[], int, double)
     */
    public AssistantBuilder<T> withRAG(String... sources) {
        this.ragSources = sources;
        return this;
    }

    /**
     * Enables RAG with full control over retrieval parameters.
     *
     * <pre>{@code
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG(
     *         new String[]{"file:/data/policy.pdf", "file:/data/terms.pdf"},
     *         5,     // return top 5 relevant segments
     *         0.7    // only segments with 70%+ relevance
     *     )
     *     .build();
     * }</pre>
     *
     * @param sources    one or more document paths
     * @param maxResults maximum number of relevant segments to retrieve (default 3)
     * @param minScore   minimum relevance score, 0.0 to 1.0 (default 0.5)
     * @return this builder
     */
    public AssistantBuilder<T> withRAG(String[] sources, int maxResults, double minScore) {
        this.ragSources = sources;
        this.ragMaxResults = maxResults;
        this.ragMinScore = minScore;
        return this;
    }

    /**
     * Enables RAG from in-memory document sources (byte arrays).
     *
     * <p>Use this when your documents come from a DMS, database, REST API,
     * or any source that provides content as {@code byte[]}.</p>
     *
     * <pre>{@code
     * // From a DMS
     * byte[] pdfBytes = dmsClient.downloadDocument("DOC-12345");
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG(DocumentSource.of("policy.pdf", pdfBytes))
     *     .build();
     *
     * // From a database BLOB
     * byte[] content = resultSet.getBytes("document_content");
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG(DocumentSource.of("terms.pdf", content))
     *     .build();
     *
     * // Plain text (no file needed)
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG(DocumentSource.ofText("policy", "All employees get 25 vacation days..."))
     *     .build();
     *
     * // Multiple documents from different sources
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withRAG(
     *         DocumentSource.of("policy.pdf", dmsClient.download("policy")),
     *         DocumentSource.of("faq.txt", restApi.getFaqBytes()),
     *         DocumentSource.ofText("extra-rules", additionalRulesText)
     *     )
     *     .build();
     * }</pre>
     *
     * @param sources one or more document sources with content as byte arrays
     * @return this builder
     * @see DocumentSource
     * @see DocumentSource#of(String, byte[])
     * @see DocumentSource#ofText(String, String)
     */
    public AssistantBuilder<T> withRAG(DocumentSource... sources) {
        this.ragDocumentSources = List.of(sources);
        return this;
    }

    /**
     * Enables RAG from in-memory document sources with tuning parameters.
     *
     * @param sources    document sources with content as byte arrays
     * @param maxResults maximum number of relevant segments to retrieve
     * @param minScore   minimum relevance score, 0.0 to 1.0
     * @return this builder
     */
    public AssistantBuilder<T> withRAG(List<DocumentSource> sources, int maxResults, double minScore) {
        this.ragDocumentSources = sources;
        this.ragMaxResults = maxResults;
        this.ragMinScore = minScore;
        return this;
    }

    public AssistantBuilder<T> withChatLanguageModel(ChatLanguageModel model) {
        this.externalModel = model;
        return this;
    }

    public T build() {
        ChatLanguageModel model = externalModel != null
                ? externalModel
                : ModelFactory.create(effectiveConfig());

        AiServices<T> serviceBuilder = AiServices.builder(assistantInterface)
                .chatLanguageModel(model);

        // Memory
        if (memorySize > 0) {
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(memorySize);
            serviceBuilder.chatMemory(memory);
        }

        // System message
        if (systemMessage != null && !systemMessage.isBlank()) {
            serviceBuilder.systemMessageProvider(chatMemoryId -> systemMessage);
        }

        // Auto-tool registration (without @Tool)
        if (!toolObjects.isEmpty()) {
            List<ToolMethod> toolMethods = ToolIntrospector.introspect(toolObjects.toArray());
            Map<ToolSpecification, ToolExecutor> toolMap = new HashMap<>();

            for (ToolMethod tm : toolMethods) {
                toolMap.put(tm.specification(), (toolExecutionRequest, memoryId) -> {
                    try {
                        // Parse arguments from JSON
                        Object[] args = parseArguments(tm, toolExecutionRequest.arguments());
                        Object result = tm.method().invoke(tm.targetObject(), args);
                        return result != null ? result.toString() : "null";
                    } catch (Exception e) {
                        return "Error executing tool: " + e.getMessage();
                    }
                });
            }

            serviceBuilder.tools(toolMap);
        }

        // RAG support - programmatic withRAG() takes precedence over @EasyRAG
        // Priority: DocumentSource (bytes) > String paths > @EasyRAG annotation
        ContentRetriever contentRetriever = null;
        if (ragDocumentSources != null && !ragDocumentSources.isEmpty()) {
            contentRetriever = RagEngine.createRetriever(ragDocumentSources, ragMaxResults, ragMinScore);
        } else if (ragSources != null && ragSources.length > 0) {
            contentRetriever = RagEngine.createRetriever(ragSources, ragMaxResults, ragMinScore);
        } else {
            EasyRAG ragAnnotation = assistantInterface.getAnnotation(EasyRAG.class);
            if (ragAnnotation != null) {
                contentRetriever = RagEngine.createRetriever(ragAnnotation);
            }
        }
        if (contentRetriever != null) {
            serviceBuilder.contentRetriever(contentRetriever);
        }

        return serviceBuilder.build();
    }

    private Object[] parseArguments(ToolMethod tm, String argumentsJson) {
        java.lang.reflect.Parameter[] params = tm.method().getParameters();
        if (params.length == 0) {
            return new Object[0];
        }

        // Parse JSON arguments using simple approach
        // LangChain4J sends arguments as a JSON object like {"param1": "value1", "param2": 42}
        try {
            com.google.gson.JsonObject jsonObj = com.google.gson.JsonParser
                    .parseString(argumentsJson).getAsJsonObject();

            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                com.google.gson.JsonElement element = jsonObj.get(paramName);

                if (element == null || element.isJsonNull()) {
                    args[i] = getDefault(params[i].getType());
                } else {
                    args[i] = convertJsonElement(element, params[i].getType());
                }
            }
            return args;
        } catch (Exception e) {
            // Fallback: if single parameter, try to use the whole string
            if (params.length == 1 && params[0].getType() == String.class) {
                return new Object[]{argumentsJson};
            }
            throw new RuntimeException("Failed to parse tool arguments: " + argumentsJson, e);
        }
    }

    private Object convertJsonElement(com.google.gson.JsonElement element, Class<?> type) {
        if (type == String.class) return element.getAsString();
        if (type == int.class || type == Integer.class) return element.getAsInt();
        if (type == long.class || type == Long.class) return element.getAsLong();
        if (type == double.class || type == Double.class) return element.getAsDouble();
        if (type == float.class || type == Float.class) return element.getAsFloat();
        if (type == boolean.class || type == Boolean.class) return element.getAsBoolean();
        return element.toString();
    }

    private Object getDefault(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        return null;
    }

    // Priority: easyai.properties < EasyAI.configure() < builder .withXxx()
    private EasyAIConfig effectiveConfig() {
        EasyAIConfig base = EasyAI.getGlobalConfig();
        return EasyAIConfigLoader.applyOverrides(base, configOverrides.build());
    }
}
