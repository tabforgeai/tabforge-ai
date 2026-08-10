package dyntabs.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Main entry point for the EasyAI library.
 *
 * <p>EasyAI is a simple abstraction layer over LangChain4J. It hides all the low-level
 * details (ChatModel, ChatMemory, AiServices, ToolSpecification, EmbeddingStore...)
 * behind a clean builder-pattern API that any Java developer can use in minutes.</p>
 *
 * <h2>Quick Start</h2>
 *
 * <p><b>Step 1:</b> Add your API key to {@code easyai.properties} on the classpath:</p>
 * <pre>
 * easyai.provider=openai
 * easyai.api-key=sk-YOUR-KEY
 * easyai.model-name=gpt-4o-mini
 * </pre>
 *
 * <p><b>Step 2:</b> Start chatting!</p>
 *
 * <h2>Three Ways to Use EasyAI</h2>
 *
 * <h3>1. Simple Chat ({@link #chat()})</h3>
 * <p>Send messages to AI and get responses. Optionally remembers conversation history.</p>
 * <pre>{@code
 * Conversation chat = EasyAI.chat()
 *     .withMemory(20)                              // remember last 20 messages
 *     .withSystemMessage("You are a helpful tutor") // set AI personality
 *     .build();
 *
 * String answer = chat.send("What is Java?");
 * String follow = chat.send("Give me an example"); // AI remembers the context
 * }</pre>
 *
 * <h3>2. AI Assistant with Tools ({@link #assistant(Class)})</h3>
 * <p>Define an interface, give it your existing service classes as "tools",
 * and the AI will call your Java methods when needed.</p>
 * <pre>{@code
 * // 1. Define assistant interface
 * @EasyAIAssistant(systemMessage = "You are an e-commerce support bot")
 * public interface SupportBot {
 *     String ask(String question);
 * }
 *
 * // 2. Your existing service — mark callable methods with @EasyTool (opt-in)
 * public class OrderService {
 *     @EasyTool("Finds an order by its ID")
 *     public String findOrder(String orderId) {
 *         return database.findById(orderId).toString();
 *     }
 * }
 *
 * // 3. Wire it together
 * SupportBot bot = EasyAI.assistant(SupportBot.class)
 *     .withTools(orderService, userService)   // only @EasyTool methods are exposed
 *     .build();
 *
 * // 4. The AI will automatically call orderService.findOrder("12345")
 * String answer = bot.ask("Where is my order #12345?");
 * }</pre>
 *
 * <h3>3. Document-Powered Assistant (RAG)</h3>
 * <p>Let the AI answer questions based on your PDF, DOCX, or TXT files.</p>
 * <pre>{@code
 * @EasyRAG(source = "classpath:company-policy.pdf")
 * @EasyAIAssistant(systemMessage = "Answer based on the company policy")
 * public interface PolicyBot {
 *     String ask(String question);
 * }
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class).build();
 * String answer = bot.ask("What is the vacation policy?");
 * // AI reads the PDF and answers based on its content
 * }</pre>
 *
 * <h2>Overriding Configuration Per-Call</h2>
 * <p>You can override any config property when building:</p>
 * <pre>{@code
 * Conversation chat = EasyAI.chat()
 *     .withProvider("ollama")                       // use local Ollama
 *     .withModel("llama3")                          // specific model
 *     .withBaseUrl("http://localhost:11434/v1/")     // custom endpoint
 *     .withTemperature(0.3)                          // less creative
 *     .withMaxTokens(500)                            // shorter answers
 *     .build();
 * }</pre>
 *
 * <h2>CDI / Jakarta EE Integration</h2>
 * <p>In a Jakarta EE application, assistants are automatically injectable:</p>
 * <pre>{@code
 * @Inject SupportBot bot;  // no manual build() needed
 * }</pre>
 *
 * @see Conversation
 * @see ConversationBuilder
 * @see AssistantBuilder
 * @see AgentBuilder
 * @see EasyAgent
 * @see FlowBuilder
 * @see Flow
 * @see EasyAIConfig
 * @see dyntabs.ai.annotation.EasyAIAssistant
 * @see dyntabs.ai.annotation.EasyRAG
 * @see dyntabs.ai.annotation.EasyTool
 */
public final class EasyAI {

    private static volatile EasyAIConfig globalConfig;

    private EasyAI() {
    }

    /**
     * Starts building a new {@link Conversation} for simple chat.
     *
     * <pre>{@code
     * Conversation chat = EasyAI.chat()
     *     .withMemory(20)
     *     .withSystemMessage("You are a helpful assistant")
     *     .build();
     *
     * String answer = chat.send("Hello!");
     * }</pre>
     *
     * @return a new {@link ConversationBuilder}
     */
    public static ConversationBuilder chat() {
        return new ConversationBuilder();
    }

    /**
     * Starts building an AI Assistant proxy for the given interface.
     *
     * <p>The interface should have one or more methods that accept a String
     * and return a String. Annotate it with {@code @EasyAIAssistant} for
     * a system message.</p>
     *
     * <pre>{@code
     * @EasyAIAssistant(systemMessage = "You are a code reviewer")
     * public interface CodeReviewer {
     *     String review(String code);
     * }
     *
     * CodeReviewer reviewer = EasyAI.assistant(CodeReviewer.class).build();
     * String feedback = reviewer.review("public void foo() { ... }");
     * }</pre>
     *
     * @param <T>                the assistant interface type
     * @param assistantInterface the interface class to create a proxy for
     * @return a new {@link AssistantBuilder}
     */
    public static <T> AssistantBuilder<T> assistant(Class<T> assistantInterface) {
        return new AssistantBuilder<>(assistantInterface);
    }

    /**
     * Starts building an {@link EasyAgent} that autonomously plans and executes
     * multi-step tasks by orchestrating calls to your registered Java services.
     *
     * <p>Unlike {@link #assistant(Class)}, which answers a single question,
     * an agent receives a complex task and breaks it into steps — calling your
     * service methods in the right order, using the result of each step to
     * decide what to do next, and adapting if a step fails.</p>
     *
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
     * String result = agent.execute(
     *     "Order 2 laptops for user U123, apply loyalty credit, " +
     *     "use fallback warehouse WH-EU if out of stock."
     * );
     * }</pre>
     *
     * @return a new {@link AgentBuilder}
     */
    public static AgentBuilder agent() {
        return new AgentBuilder();
    }

    /**
     * Starts building a {@link Flow}: a deterministic, developer-authored pipeline of named steps
     * where <em>you</em> fix the order in plain Java and the LLM is called only at the edges you
     * declare (typically "understand the request" first, "summarize the outcome" last).
     *
     * <p>This is the deliberate counterpart to {@link #agent()}. An agent lets the model decide
     * which service to call next — right when the path is genuinely unknown, but non-deterministic
     * and hard to test. A flow is for a <b>known business process</b> (check stock → take payment →
     * create order → ship): one correct order, the same every time, each step a plain, testable
     * function. The motto: <em>LLM for language, Java for logic.</em></p>
     *
     * <pre>{@code
     * FlowContext out = EasyAI.flow()
     *     .step("understand", ctx -> EasyAI.extract(OrderRequest.class).from(ctx.inputText())) // LLM
     *     .step("checkStock", ctx -> inventory.checkStock(ctx.get("understand", OrderRequest.class)))
     *     .step("pay",        ctx -> payment.charge(ctx.get("understand", OrderRequest.class)))
     *     .step("createOrder",ctx -> orders.create(ctx.get("understand", OrderRequest.class)))
     *     .step("ship",       ctx -> shipping.schedule(ctx.get("createOrder", Order.class)))
     *     .step("summarize",  ctx -> EasyAI.chat().build()
     *                                    .send("Tell the user what happened:\n" + ctx.trail()))   // LLM
     *     .build()
     *     .run("Order 3 blue watches, ship home.");
     *
     * String reply = (String) out.result();   // the "summarize" step's output
     * }</pre>
     *
     * @return a new {@link FlowBuilder}
     * @see FlowBuilder
     * @see Flow
     * @see dyntabs.ai.flow.FlowContext
     * @see dyntabs.ai.flow.FlowStep
     */
    public static FlowBuilder flow() {
        return new FlowBuilder();
    }

    /**
     * Starts a typed extraction: turn unstructured text or a document into a populated Java
     * object (record or POJO).
     *
     * <p>This is the bridge from the unstructured/AI world into your normal typed-Java world.
     * Once {@code .from(...)} returns, no AI is involved any more — you hold a plain object
     * that your existing services, JPA entities, and PrimeFaces forms already understand.</p>
     *
     * <pre>{@code
     * record Invoice(String vendor, String invoiceNumber, LocalDate date,
     *                BigDecimal total, List<LineItem> items) {}
     *
     * // From free text:
     * Invoice inv = EasyAI.extract(Invoice.class).from(emailBody);
     *
     * // From a document's bytes - parses (Tika) and extracts in one call:
     * Invoice inv = EasyAI.extract(Invoice.class)
     *     .from(DocumentSource.of("invoice.pdf", pdfBytes));
     *
     * em.persist(inv);   // it is just data from here on
     * }</pre>
     *
     * <p>Malformed model output is retried automatically; call {@code .validate()} to also run
     * Jakarta Bean Validation on the result.</p>
     *
     * @param <T>  the type to extract
     * @param type the class to extract (a record or POJO)
     * @return a new {@link ExtractionBuilder}
     * @see ExtractionBuilder
     */
    public static <T> ExtractionBuilder<T> extract(Class<T> type) {
        return new ExtractionBuilder<>(type);
    }

    /**
     * Starts building a document indexer that persists embeddings into a vector store
     * (currently Milvus), so assistants can retrieve them later.
     *
     * <p>This is the <b>write side</b> of RAG. Where {@link #assistant(Class)} with
     * {@code withRAG(...)} builds an ephemeral, in-memory index that vanishes when the JVM
     * stops, {@code indexer()} writes to a persistent store you populate once and reuse —
     * think "save the documents to the database" rather than "load them for this request."</p>
     *
     * <pre>{@code
     * // One-time (or scheduled) ingestion:
     * int indexed = EasyAI.indexer()
     *     .toMilvus("localhost", 19530, "documents")
     *     .index("file:/data/policy.pdf", "classpath:faq.txt");
     *
     * // Later, any assistant reads from the same collection:
     * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
     *     .withMilvus("localhost", 19530, "documents")
     *     .build();
     * }</pre>
     *
     * @return a new {@link IndexerBuilder}
     * @see IndexerBuilder
     * @see EasyIndexer
     * @see AssistantBuilder#withMilvus(String, int, String)
     */
    public static IndexerBuilder indexer() {
        return new IndexerBuilder();
    }

    /**
     * Sets a global configuration that will be used as default for all
     * new conversations and assistants (unless overridden per-builder).
     *
     * <pre>{@code
     * EasyAI.configure(EasyAIConfig.builder()
     *     .provider("openai")
     *     .apiKey("sk-...")
     *     .modelName("gpt-4o")
     *     .build());
     * }</pre>
     *
     * @param config the global configuration
     */
    public static void configure(EasyAIConfig config) {
        globalConfig = config;
    }

    /**
     * Returns the global configuration, or loads from {@code easyai.properties} if not set.
     *
     * @return the current global {@link EasyAIConfig}
     */
    public static EasyAIConfig getGlobalConfig() {
        if (globalConfig == null) {
            globalConfig = EasyAIConfigLoader.load();
        }
        return globalConfig;
    }

    /**
     * Extracts a clean, human-readable error message from an AI exception.
     *
     * <p>LangChain4J exceptions (especially from OpenAI-compatible providers like
     * Groq, Azure OpenAI, and OpenAI itself) often carry raw JSON in their message,
     * for example:</p>
     * <pre>
     * {"error":{"message":"Failed to call a function...","type":"invalid_request_error",...}}
     * </pre>
     * <p>This method parses the JSON and extracts only the {@code error.message} field.
     * If the message is not JSON, it is returned as-is. If the exception is {@code null},
     * an empty string is returned.</p>
     *
     * <p>Typical usage in a JSF backing bean or REST endpoint:</p>
     * <pre>{@code
     * try {
     *     return bot.ask(userQuestion);
     * } catch (Exception e) {
     *     log.error("AI call failed", e);
     *     return "Sorry, something went wrong: " + EasyAI.extractErrorMessage(e);
     * }
     * }</pre>
     *
     * <p>Common LangChain4J exception types to catch separately if needed:</p>
     * <ul>
     *   <li>{@code dev.langchain4j.exception.AuthenticationException} — wrong API key</li>
     *   <li>{@code dev.langchain4j.exception.RateLimitException} — rate limit exceeded</li>
     *   <li>{@code dev.langchain4j.exception.InvalidRequestException} — bad request, tool call failure</li>
     *   <li>{@code dev.langchain4j.exception.InternalServerException} — provider server error</li>
     * </ul>
     *
     * @param t the exception thrown by an AI assistant or conversation call
     * @return a clean, readable error message
     */
    public static String extractErrorMessage(Throwable t) {
        if (t == null) {
            return "";
        }
        String raw = t.getMessage();
        if (raw != null && raw.trim().startsWith("{")) {
            try {
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                JsonObject error = json.getAsJsonObject("error");
                if (error != null) {
                    JsonElement message = error.get("message");
                    if (message != null) {
                        return message.getAsString();
                    }
                }
            } catch (Exception ignored) {
                // not valid JSON - fall through to raw message
            }
        }
        return raw != null ? raw : t.getClass().getSimpleName();
    }
}
