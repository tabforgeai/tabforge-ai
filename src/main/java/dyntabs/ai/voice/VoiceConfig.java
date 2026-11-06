package dyntabs.ai.voice;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable settings for the two voice directions — <b>speech&nbsp;→&nbsp;text</b> (transcription)
 * and <b>text&nbsp;→&nbsp;speech</b> (synthesis).
 *
 * <h2>The one idea that makes this "easy"</h2>
 * <p>Voice settings <b>inherit from your chat settings</b>. If {@code easyai.properties} already
 * configures a provider and key for {@code EasyAI.chat()}, then {@link #forListening()} and
 * {@link #forSpeaking()} reuse that same provider, key and base URL — so adding voice to an app
 * usually costs <em>zero</em> new configuration. You only write an {@code easyai.stt.*} or
 * {@code easyai.tts.*} key when you want voice to differ from chat.</p>
 *
 * <h2>Resolution order (first non-null wins)</h2>
 * <table border="1">
 *   <caption>Where each value comes from</caption>
 *   <tr><th>Setting</th><th>Listening ({@code forListening()})</th><th>Speaking ({@code forSpeaking()})</th></tr>
 *   <tr><td>provider</td><td>{@code easyai.stt.provider} → {@code easyai.provider} → {@code "openai"}</td>
 *                        <td>{@code easyai.tts.provider} → {@code easyai.provider} → {@code "openai"}</td></tr>
 *   <tr><td>API key</td><td>{@code easyai.stt.api-key} → {@code easyai.api-key}</td>
 *                       <td>{@code easyai.tts.api-key} → {@code easyai.api-key}</td></tr>
 *   <tr><td>base URL</td><td>{@code easyai.stt.base-url} → {@code easyai.base-url}</td>
 *                        <td>{@code easyai.tts.base-url} → {@code easyai.base-url}</td></tr>
 *   <tr><td>model</td><td>{@code easyai.stt.model-name} → {@value #DEFAULT_TRANSCRIPTION_MODEL}</td>
 *                     <td>{@code easyai.tts.model-name} → {@value #DEFAULT_SPEECH_MODEL}</td></tr>
 *   <tr><td>extra</td><td>{@code easyai.stt.language} (ISO-639-1, optional)</td>
 *                     <td>{@code easyai.tts.voice} → {@value #DEFAULT_VOICE}</td></tr>
 * </table>
 *
 * <h2>Choosing a model name</h2>
 * <p>The defaults are OpenAI's own ({@value #DEFAULT_TRANSCRIPTION_MODEL},
 * {@value #DEFAULT_SPEECH_MODEL}). <b>Deliberately, nothing is guessed from the base URL.</b> If you
 * point the base URL at another OpenAI-compatible service, name its model explicitly — for Groq,
 * for instance, {@code easyai.stt.model-name=whisper-large-v3}. A wrong model name fails loudly at
 * the provider, which is easier to debug than a library quietly picking a different one.</p>
 *
 * <p><b>Analogy:</b> a household's utility accounts. The lights and the heating bill to the same
 * account unless you deliberately open a second one — and nobody rewires the house to guess which
 * supplier you meant.</p>
 *
 * @see dyntabs.ai.EasyAI#listen()
 * @see dyntabs.ai.EasyAI#speak()
 */
public final class VoiceConfig {

    private static final Logger log = LoggerFactory.getLogger(VoiceConfig.class);

    private static final String PROPERTIES_FILE = "easyai.properties";

    /** Default transcription model ({@code whisper-1} — OpenAI's own). */
    public static final String DEFAULT_TRANSCRIPTION_MODEL = "whisper-1";

    /** Default speech-synthesis model ({@code tts-1} — OpenAI's own). */
    public static final String DEFAULT_SPEECH_MODEL = "tts-1";

    /** Default synthesis voice ({@code alloy}). */
    public static final String DEFAULT_VOICE = "alloy";

    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final String language;
    private final String voice;

    private VoiceConfig(Builder builder) {
        this.provider = builder.provider;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.modelName = builder.modelName;
        this.language = builder.language;
        this.voice = builder.voice;
    }

    /** @return the provider id, e.g. {@code "openai"} (also covers any OpenAI-compatible endpoint). */
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

    /** @return the model name to call (never {@code null} once loaded from properties). */
    public String modelName() {
        return modelName;
    }

    /** @return the expected spoken language as an ISO-639-1 code, or {@code null} to auto-detect. */
    public String language() {
        return language;
    }

    /** @return the synthesis voice name, or {@code null} for the provider's default. */
    public String voice() {
        return voice;
    }

    /** @return a builder pre-filled with this configuration, for making a modified copy. */
    public Builder toBuilder() {
        return new Builder()
                .provider(provider)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .language(language)
                .voice(voice);
    }

    /** @return a new, empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load the transcription (speech&nbsp;→&nbsp;text) configuration from {@code easyai.properties},
     * falling back to the chat settings as described in the class documentation.
     *
     * @return settings for {@link dyntabs.ai.EasyAI#listen()}
     */
    public static VoiceConfig forListening() {
        return forListening(loadProperties());
    }

    /**
     * Same as {@link #forListening()} but reading an already-loaded {@link Properties} — the seam
     * that lets the resolution rules be unit-tested without planting a real
     * {@code easyai.properties} on the test classpath (which every other test would also see).
     *
     * @param props the properties to resolve from
     * @return settings for {@link dyntabs.ai.EasyAI#listen()}
     */
    static VoiceConfig forListening(Properties props) {
        return builder()
                .provider(first(props, "easyai.stt.provider", "easyai.provider", "openai"))
                .apiKey(first(props, "easyai.stt.api-key", "easyai.api-key", null))
                .baseUrl(first(props, "easyai.stt.base-url", "easyai.base-url", null))
                .modelName(first(props, "easyai.stt.model-name", null, DEFAULT_TRANSCRIPTION_MODEL))
                .language(first(props, "easyai.stt.language", null, null))
                .build();
    }

    /**
     * Load the synthesis (text&nbsp;→&nbsp;speech) configuration from {@code easyai.properties},
     * falling back to the chat settings as described in the class documentation.
     *
     * @return settings for {@link dyntabs.ai.EasyAI#speak()}
     */
    public static VoiceConfig forSpeaking() {
        return forSpeaking(loadProperties());
    }

    /**
     * Same as {@link #forSpeaking()} but reading an already-loaded {@link Properties} — see
     * {@link #forListening(Properties)} for why this seam exists.
     *
     * @param props the properties to resolve from
     * @return settings for {@link dyntabs.ai.EasyAI#speak()}
     */
    static VoiceConfig forSpeaking(Properties props) {
        return builder()
                .provider(first(props, "easyai.tts.provider", "easyai.provider", "openai"))
                .apiKey(first(props, "easyai.tts.api-key", "easyai.api-key", null))
                .baseUrl(first(props, "easyai.tts.base-url", "easyai.base-url", null))
                .modelName(first(props, "easyai.tts.model-name", null, DEFAULT_SPEECH_MODEL))
                .voice(first(props, "easyai.tts.voice", null, DEFAULT_VOICE))
                .build();
    }

    /**
     * Read {@code specificKey}, then {@code fallbackKey}, then return {@code defaultValue}.
     * Blank values count as absent, so an empty line in the properties file does not shadow a
     * perfectly good fallback — which is exactly the mistake a half-filled template invites.
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
                log.debug("{} not found on classpath; voice config relies on defaults", PROPERTIES_FILE);
            }
        } catch (IOException e) {
            log.warn("Failed to load {} for voice config: {}", PROPERTIES_FILE, e.getMessage());
        }
        return props;
    }

    @Override
    public String toString() {
        // The API key is deliberately never printed.
        return "VoiceConfig[provider=" + provider + ", model=" + modelName
                + (baseUrl != null ? ", baseUrl=" + baseUrl : "")
                + (language != null ? ", language=" + language : "")
                + (voice != null ? ", voice=" + voice : "")
                + ", apiKey=" + (isBlank(apiKey) ? "ABSENT" : "present") + "]";
    }

    /** Fluent builder for {@link VoiceConfig}; see the class documentation for defaults. */
    public static final class Builder {

        private String provider = "openai";
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private String language;
        private String voice;

        /**
         * @param provider provider id; {@code "openai"} also covers OpenAI-compatible endpoints
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
         * @param baseUrl base URL override (e.g. {@code https://api.groq.com/openai/v1/})
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param modelName the model to call (e.g. {@code whisper-1}, {@code whisper-large-v3}, {@code tts-1})
         * @return this builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * @param language expected spoken language as ISO-639-1 (e.g. {@code "sr"}); {@code null} = auto-detect
         * @return this builder
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * @param voice synthesis voice name (e.g. {@code "alloy"})
         * @return this builder
         */
        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        /** @return the immutable configuration. */
        public VoiceConfig build() {
            return new VoiceConfig(this);
        }
    }
}
