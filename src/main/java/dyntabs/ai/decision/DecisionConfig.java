package dyntabs.ai.decision;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable settings for {@code EasyAI.decide(...)} — the cheap, probability-returning decision call.
 *
 * <h2>Resolution order (first non-blank wins)</h2>
 * <ul>
 *   <li>provider: {@code easyai.decision.provider} → {@code easyai.provider} → {@code "openai"}</li>
 *   <li>API key: {@code easyai.decision.api-key} → {@code easyai.api-key}</li>
 *   <li>base URL: {@code easyai.decision.base-url} → {@code easyai.base-url}</li>
 *   <li>model: {@code easyai.decision.model-name} → {@value #DEFAULT_MODEL}</li>
 * </ul>
 *
 * <h2 id="not-openai-compatible">⚠ Read this if your chat provider is not OpenAI</h2>
 * <p>A decision model is a <b>dedicated endpoint</b>, not a chat completion with an extra parameter.
 * OpenAI serves it at {@code POST {baseUrl}/decisions} with its own small model. OpenAI-compatible
 * <em>chat</em> providers such as Groq or DeepSeek do not implement that endpoint at all and answer
 * 404, so "OpenAI-compatible" buys you nothing here. So unlike
 * {@link dyntabs.ai.voice.VoiceConfig voice}, where inheriting the chat endpoint is almost always
 * right, here inheritance is only right when your chat provider <b>is</b> OpenAI.</p>
 * <p>If it is not, set both explicitly:</p>
 * <pre>
 * easyai.decision.api-key=sk-...
 * easyai.decision.base-url=https://api.openai.com/v1/
 * </pre>
 * <p>We inherit anyway rather than guessing, because a provider-level authentication or 404 error is
 * unambiguous, whereas a library quietly swapping your endpoint is not.</p>
 *
 * <p><b>Analogy:</b> a precision instrument that needs mains power. It will accept the socket the
 * rest of the workshop uses, but if that socket is a different voltage you must say so — the
 * instrument is not going to guess, and a blown fuse tells you more than a silent adapter.</p>
 *
 * @see dyntabs.ai.EasyAI#decide(String)
 */
public final class DecisionConfig {

    private static final Logger log = LoggerFactory.getLogger(DecisionConfig.class);

    private static final String PROPERTIES_FILE = "easyai.properties";

    /**
     * Default decision model. OpenAI exposes a dedicated, small model for this call — it is not a
     * chat model, which is the whole point: one fast, cheap call instead of a full generation.
     */
    public static final String DEFAULT_MODEL = "gpt-6-luna";

    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;

    private DecisionConfig(Builder builder) {
        this.provider = builder.provider;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.modelName = builder.modelName;
    }

    /** @return the provider id, e.g. {@code "openai"}. */
    public String provider() {
        return provider;
    }

    /** @return the API key, or {@code null} if none was configured. */
    public String apiKey() {
        return apiKey;
    }

    /** @return the base URL override, or {@code null} to use the provider's default. */
    public String baseUrl() {
        return baseUrl;
    }

    /** @return the decision model name. */
    public String modelName() {
        return modelName;
    }

    /** @return a builder pre-filled with this configuration, for making a modified copy. */
    public Builder toBuilder() {
        return new Builder()
                .provider(provider)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName);
    }

    /** @return a new, empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load the decision configuration from {@code easyai.properties}, falling back to the chat
     * settings as described in the class documentation.
     *
     * @return settings for {@link dyntabs.ai.EasyAI#decide(String)}
     */
    public static DecisionConfig forDeciding() {
        return forDeciding(loadProperties());
    }

    /**
     * Same as {@link #forDeciding()} but reading an already-loaded {@link Properties} — the seam
     * that lets the resolution rules be unit-tested without planting a real
     * {@code easyai.properties} on the test classpath, which every other test would also see.
     *
     * @param props the properties to resolve from
     * @return settings for {@link dyntabs.ai.EasyAI#decide(String)}
     */
    static DecisionConfig forDeciding(Properties props) {
        return builder()
                .provider(first(props, "easyai.decision.provider", "easyai.provider", "openai"))
                .apiKey(first(props, "easyai.decision.api-key", "easyai.api-key", null))
                .baseUrl(first(props, "easyai.decision.base-url", "easyai.base-url", null))
                .modelName(first(props, "easyai.decision.model-name", null, DEFAULT_MODEL))
                .build();
    }

    /**
     * Read {@code specificKey}, then {@code fallbackKey}, then return {@code defaultValue}. Blank
     * counts as absent, so an empty line in a half-filled template cannot shadow a good fallback.
     */
    private static String first(Properties props, String specificKey, String fallbackKey, String defaultValue) {
        String v = props.getProperty(specificKey);
        if (isBlank(v) && fallbackKey != null) {
            v = props.getProperty(fallbackKey);
        }
        return isBlank(v) ? defaultValue : v.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Load {@code easyai.properties} from the classpath; empty (not fatal) when absent. */
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                props.load(is);
            } else {
                log.debug("{} not found on classpath; decision config relies on defaults", PROPERTIES_FILE);
            }
        } catch (IOException e) {
            log.warn("Failed to load {} for decision config: {}", PROPERTIES_FILE, e.getMessage());
        }
        return props;
    }

    @Override
    public String toString() {
        // The API key is deliberately never printed.
        return "DecisionConfig[provider=" + provider + ", model=" + modelName
                + (baseUrl != null ? ", baseUrl=" + baseUrl : "")
                + ", apiKey=" + (isBlank(apiKey) ? "ABSENT" : "present") + "]";
    }

    /** Fluent builder for {@link DecisionConfig}; see the class documentation for defaults. */
    public static final class Builder {

        private String provider = "openai";
        private String apiKey;
        private String baseUrl;
        private String modelName = DEFAULT_MODEL;

        /**
         * @param provider provider id (currently {@code "openai"})
         * @return this builder
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * @param apiKey the provider API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param baseUrl base URL override; see the class documentation before pointing this at a
         *                non-OpenAI service
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param modelName the decision model to call (default {@value #DEFAULT_MODEL})
         * @return this builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /** @return the immutable configuration. */
        public DecisionConfig build() {
            return new DecisionConfig(this);
        }
    }
}
