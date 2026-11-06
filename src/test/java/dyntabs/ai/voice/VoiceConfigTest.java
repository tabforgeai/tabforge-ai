package dyntabs.ai.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the behaviour that makes the voice channel "easy": voice settings <b>inherit from the chat
 * settings</b>, so adding speech to an app that already talks to a provider costs no new config.
 *
 * <p>Resolution is exercised through the package-private {@code forListening(Properties)} /
 * {@code forSpeaking(Properties)} seam rather than a real {@code easyai.properties} on the test
 * classpath — planting one there would silently change what every other test in the suite sees.</p>
 */
class VoiceConfigTest {

    /** The properties an app would have if it configured only chat (Groq, in this example). */
    private static Properties chatOnly() {
        Properties p = new Properties();
        p.setProperty("easyai.provider", "openai");
        p.setProperty("easyai.api-key", "chat-key");
        p.setProperty("easyai.base-url", "https://api.groq.com/openai/v1/");
        p.setProperty("easyai.model-name", "llama-3.3-70b-versatile");
        return p;
    }

    @Test
    @DisplayName("listening inherits provider, key and base URL from the chat settings")
    void listeningInheritsChatSettings() {
        VoiceConfig config = VoiceConfig.forListening(chatOnly());

        assertThat(config.provider()).isEqualTo("openai");
        assertThat(config.apiKey()).isEqualTo("chat-key");
        assertThat(config.baseUrl()).isEqualTo("https://api.groq.com/openai/v1/");
    }

    @Test
    @DisplayName("listening does NOT inherit the chat model name — it uses the transcription default")
    void listeningDoesNotInheritChatModel() {
        VoiceConfig config = VoiceConfig.forListening(chatOnly());

        // Inheriting easyai.model-name would send a chat model to a transcription endpoint.
        assertThat(config.modelName()).isEqualTo(VoiceConfig.DEFAULT_TRANSCRIPTION_MODEL);
        assertThat(config.modelName()).isNotEqualTo("llama-3.3-70b-versatile");
    }

    @Test
    @DisplayName("speaking inherits the chat key and defaults both model and voice")
    void speakingInheritsAndDefaults() {
        VoiceConfig config = VoiceConfig.forSpeaking(chatOnly());

        assertThat(config.apiKey()).isEqualTo("chat-key");
        assertThat(config.modelName()).isEqualTo(VoiceConfig.DEFAULT_SPEECH_MODEL);
        assertThat(config.voice()).isEqualTo(VoiceConfig.DEFAULT_VOICE);
    }

    @Test
    @DisplayName("voice-specific keys override the inherited chat ones")
    void specificKeysWin() {
        Properties p = chatOnly();
        p.setProperty("easyai.stt.api-key", "stt-key");
        p.setProperty("easyai.stt.base-url", "https://api.openai.com/v1/");
        p.setProperty("easyai.stt.model-name", "whisper-large-v3");
        p.setProperty("easyai.stt.language", "sr");

        VoiceConfig config = VoiceConfig.forListening(p);

        assertThat(config.apiKey()).isEqualTo("stt-key");
        assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v1/");
        assertThat(config.modelName()).isEqualTo("whisper-large-v3");
        assertThat(config.language()).isEqualTo("sr");
    }

    @Test
    @DisplayName("a blank value counts as absent, so it cannot shadow a usable fallback")
    void blankValuesAreTreatedAsAbsent() {
        Properties p = chatOnly();
        p.setProperty("easyai.stt.api-key", "   ");     // half-filled template line
        p.setProperty("easyai.stt.model-name", "");

        VoiceConfig config = VoiceConfig.forListening(p);

        assertThat(config.apiKey()).isEqualTo("chat-key");                                  // fell back
        assertThat(config.modelName()).isEqualTo(VoiceConfig.DEFAULT_TRANSCRIPTION_MODEL);  // defaulted
    }

    @Test
    @DisplayName("with no properties at all, defaults apply and no key is invented")
    void emptyPropertiesYieldDefaults() {
        VoiceConfig config = VoiceConfig.forListening(new Properties());

        assertThat(config.provider()).isEqualTo("openai");
        assertThat(config.modelName()).isEqualTo(VoiceConfig.DEFAULT_TRANSCRIPTION_MODEL);
        assertThat(config.apiKey()).isNull();
        assertThat(config.baseUrl()).isNull();
    }

    @Test
    @DisplayName("language is absent unless configured, so the model auto-detects")
    void languageDefaultsToAutoDetect() {
        assertThat(VoiceConfig.forListening(chatOnly()).language()).isNull();
    }

    @Test
    @DisplayName("values are trimmed, so a stray space cannot break a URL or key")
    void valuesAreTrimmed() {
        Properties p = new Properties();
        p.setProperty("easyai.api-key", "  spaced-key  ");

        assertThat(VoiceConfig.forListening(p).apiKey()).isEqualTo("spaced-key");
    }

    @Test
    @DisplayName("toBuilder makes a modified copy without touching the original")
    void toBuilderCopies() {
        VoiceConfig original = VoiceConfig.forListening(chatOnly());
        VoiceConfig modified = original.toBuilder()
                .modelName("whisper-large-v3")
                .language("sr")
                .build();

        assertThat(modified.modelName()).isEqualTo("whisper-large-v3");
        assertThat(modified.language()).isEqualTo("sr");
        assertThat(modified.apiKey()).isEqualTo(original.apiKey());
        assertThat(original.modelName()).isEqualTo(VoiceConfig.DEFAULT_TRANSCRIPTION_MODEL);
        assertThat(original.language()).isNull();
    }

    @Test
    @DisplayName("toString never leaks the API key")
    void toStringHidesKey() {
        String text = VoiceConfig.forListening(chatOnly()).toString();

        assertThat(text).doesNotContain("chat-key");
        assertThat(text).contains("apiKey=present");
    }

    @Test
    @DisplayName("toString reports a missing key as ABSENT, which is the usual misconfiguration")
    void toStringFlagsMissingKey() {
        assertThat(VoiceConfig.forListening(new Properties()).toString()).contains("apiKey=ABSENT");
    }
}
