package dyntabs.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Builder for creating {@link Conversation} instances.
 *
 * <p>Every setting is optional. At minimum, you just need a configured
 * {@code easyai.properties} file on the classpath, then:</p>
 *
 * <pre>{@code
 * Conversation chat = EasyAI.chat().build();
 * }</pre>
 *
 * <h3>Available Options</h3>
 * <table>
 *   <tr><th>Method</th><th>What it does</th><th>Default</th></tr>
 *   <tr><td>{@link #withMemory(int)}</td><td>How many messages to remember</td><td>0 (no memory)</td></tr>
 *   <tr><td>{@link #withSystemMessage(String)}</td><td>Set the AI's personality/role</td><td>none</td></tr>
 *   <tr><td>{@link #withModel(String)}</td><td>Override the model name</td><td>from properties</td></tr>
 *   <tr><td>{@link #withApiKey(String)}</td><td>Override the API key</td><td>from properties</td></tr>
 *   <tr><td>{@link #withProvider(String)}</td><td>"openai" or "ollama"</td><td>from properties</td></tr>
 *   <tr><td>{@link #withBaseUrl(String)}</td><td>Custom API endpoint</td><td>provider default</td></tr>
 *   <tr><td>{@link #withTemperature(double)}</td><td>0.0=precise, 1.0=creative</td><td>provider default</td></tr>
 *   <tr><td>{@link #withMaxTokens(int)}</td><td>Max response length</td><td>provider default</td></tr>
 * </table>
 *
 * <h3>Example: Using Local Ollama Instead of OpenAI</h3>
 * <pre>{@code
 * Conversation chat = EasyAI.chat()
 *     .withProvider("ollama")
 *     .withModel("llama3")
 *     .withMemory(10)
 *     .build();
 * }</pre>
 *
 * @see EasyAI#chat()
 * @see Conversation
 */
public class ConversationBuilder {

    private int memorySize = 0;
    private String systemMessage;
    private final EasyAIConfig.Builder configOverrides = EasyAIConfig.builder();
    private ChatLanguageModel externalModel;

    ConversationBuilder() {
    }

    /**
     * Enables conversation memory. The AI will remember the last {@code maxMessages}
     * messages (both user and AI messages count).
     *
     * <p>Example: {@code withMemory(20)} means the AI sees the last 20 messages
     * as context when generating a response.</p>
     *
     * @param maxMessages number of messages to keep in memory (e.g. 10, 20, 50)
     * @return this builder
     */
    public ConversationBuilder withMemory(int maxMessages) {
        this.memorySize = maxMessages;
        return this;
    }

    /**
     * Sets a system message that defines the AI's behavior and personality.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"You are a helpful Java tutor"</li>
     *   <li>"You are a customer support agent for an online store"</li>
     *   <li>"Always respond in JSON format"</li>
     * </ul>
     *
     * @param systemMessage the system instruction for the AI
     * @return this builder
     */
    public ConversationBuilder withSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
        return this;
    }

    /**
     * Overrides the model name from configuration.
     *
     * <p>Examples: "gpt-4o", "gpt-4o-mini", "llama3"</p>
     *
     * @param modelName the model name
     * @return this builder
     */
    public ConversationBuilder withModel(String modelName) {
        this.configOverrides.modelName(modelName);
        return this;
    }

    /**
     * Overrides the API key from configuration.
     *
     * @param apiKey your API key
     * @return this builder
     */
    public ConversationBuilder withApiKey(String apiKey) {
        this.configOverrides.apiKey(apiKey);
        return this;
    }

    /**
     * Overrides the AI provider. Supported values: "openai", "ollama".
     *
     * @param provider the provider name
     * @return this builder
     */
    public ConversationBuilder withProvider(String provider) {
        this.configOverrides.provider(provider);
        return this;
    }

    /**
     * Overrides the API base URL.
     *
     * <p>Useful for proxies, Azure OpenAI, or self-hosted endpoints.</p>
     *
     * @param baseUrl the base URL (e.g. "http://localhost:11434/v1/")
     * @return this builder
     */
    public ConversationBuilder withBaseUrl(String baseUrl) {
        this.configOverrides.baseUrl(baseUrl);
        return this;
    }

    /**
     * Sets the temperature (creativity) of the AI responses.
     *
     * <ul>
     *   <li>0.0 = deterministic, always picks the most likely word</li>
     *   <li>0.7 = balanced (good default)</li>
     *   <li>1.0 = very creative, more random</li>
     * </ul>
     *
     * @param temperature value between 0.0 and 1.0
     * @return this builder
     */
    public ConversationBuilder withTemperature(double temperature) {
        this.configOverrides.temperature(temperature);
        return this;
    }

    /**
     * Limits the maximum number of tokens in the AI response.
     *
     * <p>Roughly: 1 token ~ 4 characters in English.</p>
     *
     * @param maxTokens maximum tokens (e.g. 500, 1000, 4000)
     * @return this builder
     */
    public ConversationBuilder withMaxTokens(int maxTokens) {
        this.configOverrides.maxTokens(maxTokens);
        return this;
    }

    /**
     * Injects an externally created ChatLanguageModel.
     *
     * <p>Useful for testing with a mock model, or when you need full control
     * over model creation.</p>
     *
     * @param model a pre-built ChatLanguageModel instance
     * @return this builder
     */
    public ConversationBuilder withChatLanguageModel(ChatLanguageModel model) {
        this.externalModel = model;
        return this;
    }

    /**
     * Builds and returns a ready-to-use {@link Conversation}.
     *
     * @return a new Conversation instance
     */
    public Conversation build() {
        ChatLanguageModel model = externalModel != null
                ? externalModel
                : ModelFactory.create(effectiveConfig());

        return new Conversation(model, systemMessage, memorySize);
    }

    // Priority: easyai.properties < EasyAI.configure() < builder .withXxx()
    private EasyAIConfig effectiveConfig() {
        EasyAIConfig base = EasyAI.getGlobalConfig();
        return EasyAIConfigLoader.applyOverrides(base, configOverrides.build());
    }
}
