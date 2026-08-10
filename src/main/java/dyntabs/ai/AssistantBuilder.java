package dyntabs.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dyntabs.ai.activity.ActivityContext;
import dyntabs.ai.annotation.EasyAIAssistant;
import dyntabs.ai.annotation.EasyRAG;
import dyntabs.ai.assistant.ToolIntrospector;
import dyntabs.ai.assistant.ToolMethod;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;
import dyntabs.ai.event.EasyAIListener;
import dyntabs.ai.event.EventEmitter;
import dyntabs.ai.rag.DocumentSource;
import dyntabs.ai.rag.MilvusConfig;
import dyntabs.ai.rag.MilvusEngine;
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
 * {@link #withTools(Object...)}, and the AI will call their {@link dyntabs.ai.annotation.EasyTool
 * @EasyTool}-annotated methods when needed. <b>Tools are opt-in:</b> only annotated methods are
 * exposed to the model; everything else stays uncallable.</p>
 * <pre>{@code
 * // Your existing service - annotate the methods the AI may call
 * public class WeatherService {
 *     @EasyTool("Returns the current weather for a city")
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
 * and reads the {@code @EasyTool} methods from the actual bean class. Method calls go through
 * the container proxy, so transactions, security, and interceptors work normally.</p>
 * <pre>{@code
 * @Stateless
 * public class OrderService {
 *     @PersistenceContext private EntityManager em;
 *
 *     @EasyTool("Finds an order by its ID")
 *     public String findOrder(String orderId) {
 *         return em.find(Order.class, orderId).toString();
 *     }
 * }
 *
 * // In your CDI bean:
 * @Inject OrderService orderService;   // EJB proxy from the container
 *
 * SupportBot bot = EasyAI.assistant(SupportBot.class)
 *     .withTools(orderService)          // only its @EasyTool methods are callable
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
    // Escape-hatch tool objects: every public method is exposed, annotated or not.
    // Kept separate from toolObjects so opt-in and expose-all never cross-contaminate.
    private final List<Object> allPublicToolObjects = new ArrayList<>();
    private int memorySize = 20;
    private ChatMemory externalChatMemory;
    private String systemMessage;
    private final EasyAIConfig.Builder configOverrides = EasyAIConfig.builder();
    private ChatModel externalModel;
    private ActivityContext activityContext;
    private EasyAIListener eventListener;

    // Programmatic RAG config (overrides @EasyRAG annotation if set)
    private String[] ragSources;
    private List<DocumentSource> ragDocumentSources;
    private int ragMaxResults = 3;
    private double ragMinScore = 0.5;

    // Persistent vector-store RAG (Milvus) — highest priority when set
    private MilvusConfig milvusConfig;

    AssistantBuilder(Class<T> assistantInterface) {
        this.assistantInterface = assistantInterface;

        // Read @EasyAIAssistant annotation for defaults
        EasyAIAssistant annotation = assistantInterface.getAnnotation(EasyAIAssistant.class);
        if (annotation != null && !annotation.systemMessage().isEmpty()) {
            this.systemMessage = annotation.systemMessage();
        }
    }

    /**
     * Adds tool objects whose {@link dyntabs.ai.annotation.EasyTool @EasyTool}-annotated
     * methods the AI can call.
     *
     * <p><b>Opt-in (since 3.0.0):</b> only methods you mark with {@code @EasyTool} become
     * tools. Every other method — including state-mutating ones like {@code cancelOrder} or
     * {@code deleteUser} — stays invisible to the model. Adding a method to a service is safe
     * by default; it does nothing until you annotate it.</p>
     *
     * <p>Accepts plain POJOs and Jakarta EJB proxies ({@code @Stateless},
     * {@code @Stateful}, {@code @Singleton}) obtained via {@code @Inject}.
     * EJB proxies are detected automatically — business methods are discovered
     * from the actual bean class, while invocations go through the proxy
     * so that container services (transactions, security, interceptors) work normally.</p>
     *
     * @param tools one or more service objects (POJOs or injected EJB proxies)
     * @return this builder
     * @see #withAllPublicMethodsAsTools(Object...)
     */
    public AssistantBuilder<T> withTools(Object... tools) {
        for (Object tool : tools) {
            this.toolObjects.add(tool);
        }
        return this;
    }

    /**
     * Adds tool objects and exposes <b>every public method</b> on them to the AI — annotated
     * or not.
     *
     * <p><b>Unsafe escape hatch.</b> This restores the pre-3.0.0 "expose everything" behavior.
     * Because a tool channel is reachable by the model (whose input is not fully trusted), this
     * lets a prompt-injected model invoke any public method on the beans you pass — a classic
     * confused-deputy exposure. Reserve it for throwaway prototypes where nothing is sensitive;
     * for anything real, prefer {@link #withTools(Object...)} with
     * {@link dyntabs.ai.annotation.EasyTool @EasyTool} on exactly the methods you intend.</p>
     *
     * @param tools one or more service objects whose entire public surface becomes callable
     * @return this builder
     * @see #withTools(Object...)
     */
    public AssistantBuilder<T> withAllPublicMethodsAsTools(Object... tools) {
        for (Object tool : tools) {
            this.allPublicToolObjects.add(tool);
        }
        return this;
    }

    public AssistantBuilder<T> withMemory(int maxMessages) {
        this.memorySize = maxMessages;
        return this;
    }

    /**
     * Uses a {@link ChatMemory} instance <b>you own and keep</b>, instead of letting the builder
     * create a fresh one sized by {@link #withMemory(int)}.
     *
     * <h2>Why this exists</h2>
     * <p>{@link #withMemory(int)} says "make me a memory of this size" — a brand-new, empty
     * conversation buffer is created inside every {@link #build()}. That is exactly what you
     * <em>don't</em> want when you have to rebuild the assistant but keep the conversation going.
     * The motivating case: an assistant whose <em>tool set changes with context</em> (e.g. a global
     * panel that exposes order tools only while the user is on the Orders tab). Tools are fixed at
     * {@code build()} time, so changing them means rebuilding — and with {@code withMemory(int)} each
     * rebuild would wipe the chat history. Hand the builder a memory you hold onto instead, pass the
     * <em>same</em> instance into every rebuild, and the conversation survives untouched.</p>
     *
     * <p>It is also the seam for a <b>custom or persistent</b> memory: supply a
     * {@code MessageWindowChatMemory} with your own store, or any other {@link ChatMemory}
     * implementation, and the assistant will read and append to it.</p>
     *
     * <p><b>Precedence:</b> when set, this wins over {@link #withMemory(int)} — the size hint is
     * ignored because you are supplying the whole memory. Pass {@code null} to fall back to the
     * size-based default.</p>
     *
     * <p><b>Familiar analogy:</b> {@code withMemory(int)} is asking the office for a fresh, empty
     * notebook each meeting; {@code withChatMemory(...)} is bringing your <em>own</em> notebook so
     * the running notes carry over from one meeting to the next — even when the meeting room (the
     * tool set) changes.</p>
     *
     * <p><b>Thread-safety:</b> a shared memory is shared mutable state. Reuse one instance only
     * within a single logical conversation (e.g. one session bean), not across concurrent users.</p>
     *
     * @param chatMemory the memory to read from and append to; {@code null} restores the
     *                   {@link #withMemory(int)} default
     * @return this builder
     * @see #withMemory(int)
     */
    public AssistantBuilder<T> withChatMemory(ChatMemory chatMemory) {
        this.externalChatMemory = chatMemory;
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

    /**
     * Connects this assistant to a <b>persistent</b> Milvus collection for retrieval,
     * using explicit connection settings.
     *
     * <p>This is the read-side counterpart to {@link EasyAI#indexer()}. Unlike
     * {@link #withRAG(String...)} — which loads and embeds documents fresh on every
     * {@code build()} into an in-memory store — {@code withMilvus} points at a collection
     * that was populated earlier (and stays populated). Think "query the existing
     * database" versus "load a file into memory for this one request."</p>
     *
     * <p>When set, Milvus takes precedence over {@code withRAG(...)} and {@code @EasyRAG}.
     * Retrieval tuning uses {@link #withRAG(String[], int, double) maxResults/minScore}
     * defaults (3 / 0.5) unless you call {@link #withMilvus(MilvusConfig, int, double)}.</p>
     *
     * <pre>{@code
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withMilvus("localhost", 19530, "documents")
     *     .build();
     * }</pre>
     *
     * @param host           Milvus server hostname
     * @param port           Milvus server port (typically 19530)
     * @param collectionName the collection to retrieve from
     * @return this builder
     */
    public AssistantBuilder<T> withMilvus(String host, int port, String collectionName) {
        this.milvusConfig = MilvusConfig.of(host, port, collectionName);
        return this;
    }

    /**
     * Connects this assistant to a persistent Milvus collection using a fully built
     * {@link MilvusConfig} (for non-default dimension or credentials).
     *
     * @param config the Milvus connection settings
     * @return this builder
     */
    public AssistantBuilder<T> withMilvus(MilvusConfig config) {
        this.milvusConfig = config;
        return this;
    }

    /**
     * Connects this assistant to a persistent Milvus collection with explicit retrieval
     * tuning.
     *
     * @param config     the Milvus connection settings
     * @param maxResults maximum relevant segments to retrieve per query
     * @param minScore   minimum relevance score, 0.0 to 1.0
     * @return this builder
     */
    public AssistantBuilder<T> withMilvus(MilvusConfig config, int maxResults, double minScore) {
        this.milvusConfig = config;
        this.ragMaxResults = maxResults;
        this.ragMinScore = minScore;
        return this;
    }

    /**
     * Connects this assistant to a persistent Milvus collection configured entirely from
     * {@code easyai.properties} (keys {@code easyai.milvus.*}).
     *
     * @return this builder
     * @see MilvusConfig#fromProperties()
     */
    public AssistantBuilder<T> withMilvus() {
        this.milvusConfig = MilvusConfig.fromProperties();
        return this;
    }

    public AssistantBuilder<T> withChatModel(ChatModel model) {
        this.externalModel = model;
        return this;
    }

    /**
     * Makes this assistant <em>ambient-activity aware</em>: before each call to any of the assistant's
     * methods, the given context is re-rendered and folded into the system message, so the model
     * already knows what the user has recently been doing in the UI (and can resolve "this"/"that"
     * without being told).
     *
     * <p><b>Familiar analogy:</b> giving your assistant a glance at your desk before every question —
     * it sees the order you just opened, so "email the customer about it" needs no further explanation.</p>
     *
     * <pre>{@code
     * SupportBot bot = EasyAI.assistant(SupportBot.class)
     *     .withTools(orderService)
     *     .withActivityContext(ActivityContext.of(activityStore)
     *         .forSession(sessionId).forTab(tabId).build())
     *     .build();
     *
     * bot.ask("Cancel this order");   // "this" resolved from recent activity
     * }</pre>
     *
     * <p>The context is combined with any {@link #withSystemMessage(String)} value by
     * {@link SystemMessageComposer}; passing {@code null} simply leaves the feature off.</p>
     *
     * @param activityContext the ambient-activity descriptor to inject, or {@code null} to disable
     * @return this builder
     * @see dyntabs.ai.activity.ActivityContext
     */
    public AssistantBuilder<T> withActivityContext(ActivityContext activityContext) {
        this.activityContext = activityContext;
        return this;
    }

    /**
     * Registers a transport-agnostic {@link EasyAIListener} that receives a live stream of
     * {@link dyntabs.ai.event.EasyAIEvent}s as the assistant calls your tools.
     *
     * <p>This is the same observability hook the other capabilities expose (see
     * {@link AgentBuilder#withEventListener(EasyAIListener)} and
     * {@link FlowBuilder#withEventListener(EasyAIListener)}). Because an assistant's method is invoked
     * directly by your code (there is no wrapper for EasyAI to bracket), the stream here is the
     * per-<b>tool-call</b> slice of the lifecycle: for each tool the model decides to call, a
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#STEP_STARTED} fires before it runs (a spinning row in a
     * UI) and a {@link dyntabs.ai.event.EasyAIEvent.Phase#STEP} fires after, with
     * {@link dyntabs.ai.event.EasyAIEvent.Status#SUCCESS} or
     * {@link dyntabs.ai.event.EasyAIEvent.Status#ERROR}. Events are emitted under
     * {@link dyntabs.ai.event.EasyAIEvent.Source#ASSISTANT}. An assistant with no tools emits nothing.</p>
     *
     * <p><b>Familiar analogy:</b> a live camera over the assistant's hands — you see each tool it
     * reaches for and what came back, even though the final spoken answer arrives through the normal
     * return value of your assistant method.</p>
     *
     * <pre>{@code
     * SupportBot bot = EasyAI.assistant(SupportBot.class)
     *     .withTools(orderService)
     *     .withEventListener(e -> log.info("{}", e))   // tool_call → tool_result per tool
     *     .build();
     * }</pre>
     *
     * @param eventListener the listener to receive the assistant's tool-call event stream (may be {@code null})
     * @return this builder
     * @see dyntabs.ai.event.EasyAIEvent
     * @see dyntabs.ai.event.EasyAIListener
     */
    public AssistantBuilder<T> withEventListener(EasyAIListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    public T build() {
        ChatModel model = externalModel != null
                ? externalModel
                : ModelFactory.create(effectiveConfig());

        // Live event stream (no-op when no listener was registered).
        EventEmitter emitter = new EventEmitter(Source.ASSISTANT, eventListener);

        AiServices<T> serviceBuilder = AiServices.builder(assistantInterface)
                .chatModel(model);

        // Memory. A caller-supplied ChatMemory (withChatMemory) wins over the size-based default
        // (withMemory) — that is how a rebuilt assistant keeps the same conversation: hand it the
        // same memory instance each time instead of minting a fresh, empty one here.
        if (externalChatMemory != null) {
            serviceBuilder.chatMemory(externalChatMemory);
        } else if (memorySize > 0) {
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(memorySize);
            serviceBuilder.chatMemory(memory);
        }

        // System message. Install a provider when there is either a static message OR an ambient
        // activity context; the lambda runs on every assistant-method call, so the activity briefing
        // it folds in is re-rendered fresh each time (see SystemMessageComposer).
        boolean hasSystemMessage = systemMessage != null && !systemMessage.isBlank();
        if (hasSystemMessage || activityContext != null) {
            serviceBuilder.systemMessageProvider(
                    chatMemoryId -> SystemMessageComposer.compose(systemMessage, activityContext));
        }

        // Tool registration. Opt-in objects expose only @EasyTool methods; escape-hatch
        // objects expose their whole public surface. Merge both into a single tool map.
        if (!toolObjects.isEmpty() || !allPublicToolObjects.isEmpty()) {
            List<ToolMethod> toolMethods = new ArrayList<>();
            if (!toolObjects.isEmpty()) {
                toolMethods.addAll(ToolIntrospector.introspect(toolObjects.toArray()));
            }
            if (!allPublicToolObjects.isEmpty()) {
                toolMethods.addAll(ToolIntrospector.introspectAllPublic(allPublicToolObjects.toArray()));
            }
            Map<ToolSpecification, ToolExecutor> toolMap = new HashMap<>();

            for (ToolMethod tm : toolMethods) {
                toolMap.put(tm.specification(), (toolExecutionRequest, memoryId) -> {
                    // Pre-step: announce the tool dispatch before it runs (spinner row in a UI).
                    emitter.stepStarted(tm.specification().name(), toolExecutionRequest.arguments());

                    String result;
                    Status status;
                    try {
                        // Parse arguments from JSON
                        Object[] args = parseArguments(tm, toolExecutionRequest.arguments());
                        Object rawResult = tm.method().invoke(tm.targetObject(), args);
                        result = rawResult != null ? rawResult.toString() : "null";
                        status = Status.SUCCESS;
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        result = "Error executing tool: " + cause.getMessage();
                        status = Status.ERROR;
                    } catch (Exception e) {
                        result = "Error executing tool: " + e.getMessage();
                        status = Status.ERROR;
                    }

                    // Post-step: the tool returned — resolve the row to success or error.
                    emitter.step(tm.specification().name(), result, status);
                    return result;
                });
            }

            serviceBuilder.tools(toolMap);
        }

        // RAG support - programmatic config takes precedence over @EasyRAG
        // Priority: Milvus (persistent) > DocumentSource (bytes) > String paths > @EasyRAG annotation
        ContentRetriever contentRetriever = null;
        if (milvusConfig != null) {
            contentRetriever = MilvusEngine.createRetriever(milvusConfig, ragMaxResults, ragMinScore);
        } else if (ragDocumentSources != null && !ragDocumentSources.isEmpty()) {
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
