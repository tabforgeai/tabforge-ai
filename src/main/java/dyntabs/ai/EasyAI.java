package dyntabs.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Main entry point for the EasyAI library.
 *
 * <p>EasyAI is a simple abstraction layer over LangChain4J. It hides all the low-level
 * details (ChatLanguageModel, ChatMemory, AiServices, ToolSpecification, EmbeddingStore...)
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
 * // 2. Your existing service (no AI annotations needed!)
 * public class OrderService {
 *     public String findOrder(String orderId) {
 *         return database.findById(orderId).toString();
 *     }
 * }
 *
 * // 3. Wire it together
 * SupportBot bot = EasyAI.assistant(SupportBot.class)
 *     .withTools(orderService, userService)
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
