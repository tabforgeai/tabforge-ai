package dyntabs.ai.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiTextToSpeechModel;

/**
 * Tests everything about the voice adapter that can be verified <b>without</b> calling a provider:
 * the model wiring, and — the part that saves a developer an afternoon — that misconfiguration
 * fails immediately with a message naming the property to set.
 */
class VoiceEngineTest {

    private static final byte[] AUDIO = new byte[] { 1, 2, 3, 4 };

    private static VoiceConfig keyed() {
        return VoiceConfig.builder().apiKey("test-key").modelName("whisper-1").build();
    }

    @Test
    @DisplayName("builds an OpenAI-compatible transcription model from config")
    void buildsTranscriptionModel() {
        assertThat(VoiceEngine.transcriptionModel(keyed()))
                .isInstanceOf(OpenAiAudioTranscriptionModel.class);
    }

    @Test
    @DisplayName("builds an OpenAI-compatible synthesis model from config")
    void buildsSpeechModel() {
        VoiceConfig config = VoiceConfig.builder()
                .apiKey("test-key").modelName("tts-1").voice("alloy").build();

        assertThat(VoiceEngine.speechModel(config)).isInstanceOf(OpenAiTextToSpeechModel.class);
    }

    @Test
    @DisplayName("a base URL is accepted, which is how any OpenAI-compatible service is targeted")
    void acceptsBaseUrl() {
        VoiceConfig config = keyed().toBuilder()
                .baseUrl("https://api.groq.com/openai/v1/")
                .modelName("whisper-large-v3")
                .build();

        assertThat(VoiceEngine.transcriptionModel(config)).isNotNull();
    }

    @Test
    @DisplayName("a missing key fails fast and names the property to set")
    void missingKeyFailsFastWithGuidance() {
        VoiceConfig noKey = VoiceConfig.builder().modelName("whisper-1").build();

        assertThatThrownBy(() -> VoiceEngine.transcribe(AUDIO, "audio/webm", noKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("easyai.stt.api-key")
                .hasMessageContaining("easyai.api-key");

        assertThatThrownBy(() -> VoiceEngine.synthesize("hello", noKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("easyai.tts.api-key");
    }

    @Test
    @DisplayName("empty audio is rejected before any provider call")
    void rejectsEmptyAudio() {
        assertThatThrownBy(() -> VoiceEngine.transcribe(new byte[0], "audio/webm", keyed()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        assertThatThrownBy(() -> VoiceEngine.transcribe(null, "audio/webm", keyed()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("blank text is rejected before any provider call")
    void rejectsBlankText() {
        VoiceConfig config = keyed().toBuilder().modelName("tts-1").build();

        assertThatThrownBy(() -> VoiceEngine.synthesize("   ", config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> VoiceEngine.synthesize(null, config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an unknown provider fails with a message saying what IS supported")
    void unknownProviderIsExplained() {
        VoiceConfig config = keyed().toBuilder().provider("ollama").build();

        assertThatThrownBy(() -> VoiceEngine.transcriptionModel(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ollama")
                .hasMessageContaining("openai");

        assertThatThrownBy(() -> VoiceEngine.speechModel(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openai");
    }

    @Test
    @DisplayName("the provider id is case-insensitive")
    void providerIsCaseInsensitive() {
        VoiceConfig config = keyed().toBuilder().provider("OpenAI").build();

        assertThat(VoiceEngine.transcriptionModel(config)).isNotNull();
    }
}
