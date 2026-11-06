package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dyntabs.ai.event.EasyAIEvent;
import dyntabs.ai.event.EasyAIEvent.Phase;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;
import dyntabs.ai.voice.VoiceConfig;

/**
 * Tests the {@code EasyAI.listen()} / {@code EasyAI.speak()} facade: that the chain reaches the
 * adapter with the per-call overrides applied, and that a voice operation narrates itself through
 * the same event stream as every other EasyAI capability.
 *
 * <p>Configs are passed explicitly rather than read from {@code easyai.properties}, so these tests
 * behave identically no matter what is (or is not) on the classpath.</p>
 */
class VoiceBuilderTest {

    private static final byte[] AUDIO = new byte[] { 9, 8, 7 };

    /** A configuration that is deliberately unusable: no API key. */
    private static VoiceConfig withoutKey() {
        return VoiceConfig.builder().modelName("whisper-1").build();
    }

    @Test
    @DisplayName("EasyAI.listen() and EasyAI.speak() hand back fresh builders")
    void facadeReturnsBuilders() {
        assertThat(EasyAI.listen()).isNotNull().isNotSameAs(EasyAI.listen());
        assertThat(EasyAI.speak()).isNotNull().isNotSameAs(EasyAI.speak());
    }

    @Test
    @DisplayName("the whole listen chain reaches the adapter — a missing key surfaces its guidance")
    void listenChainReachesAdapter() {
        assertThatThrownBy(() -> EasyAI.listen().withConfig(withoutKey()).from(AUDIO, "audio/webm"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("easyai.stt.api-key");
    }

    @Test
    @DisplayName("the whole speak chain reaches the adapter")
    void speakChainReachesAdapter() {
        assertThatThrownBy(() -> EasyAI.speak().withConfig(withoutKey()).from("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("easyai.tts.api-key");
    }

    @Test
    @DisplayName("per-call overrides are applied on top of the base config")
    void overridesAreApplied() {
        // The key override satisfies the key check, so the next gate — the unknown provider —
        // is what fails. That proves both overrides travelled all the way down.
        assertThatThrownBy(() -> EasyAI.listen()
                .withConfig(VoiceConfig.builder().provider("nope").build())
                .withApiKey("supplied-at-call-time")
                .withModel("whisper-large-v3")
                .withBaseUrl("https://api.groq.com/openai/v1/")
                .withLanguage("sr")
                .from(AUDIO, "audio/webm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    @DisplayName("a failed transcription narrates STARTED then ERROR under Source.VOICE")
    void failureIsNarratedThroughTheEventStream() {
        List<EasyAIEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> EasyAI.listen()
                .withConfig(withoutKey())
                .withEventListener(events::add)
                .from(AUDIO, "audio/webm"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).source()).isEqualTo(Source.VOICE);
        assertThat(events.get(0).phase()).isEqualTo(Phase.STARTED);
        assertThat(events.get(0).status()).isEqualTo(Status.RUNNING);
        assertThat(events.get(1).phase()).isEqualTo(Phase.ERROR);
        assertThat(events.get(1).status()).isEqualTo(Status.ERROR);
        assertThat(events.get(1).detail()).contains("easyai.stt.api-key");
        assertThat(events.get(1).sequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("a failed synthesis narrates the same way")
    void synthesisFailureIsNarrated() {
        List<EasyAIEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> EasyAI.speak()
                .withConfig(withoutKey())
                .withEventListener(events::add)
                .from("hello"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(events).extracting(EasyAIEvent::phase).containsExactly(Phase.STARTED, Phase.ERROR);
        assertThat(events).allSatisfy(e -> assertThat(e.source()).isEqualTo(Source.VOICE));
    }

    @Test
    @DisplayName("no listener means no events and no crash")
    void noListenerIsSilent() {
        assertThatThrownBy(() -> EasyAI.listen().withConfig(withoutKey()).from(AUDIO))
                .isInstanceOf(IllegalStateException.class);   // and nothing else blew up
    }

    @Test
    @DisplayName("a stream input is read before the provider is consulted")
    void acceptsStreamInput() {
        assertThatThrownBy(() -> EasyAI.listen()
                .withConfig(withoutKey())
                .from(new ByteArrayInputStream(AUDIO), "audio/webm"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("easyai.stt.api-key");
    }

    @Test
    @DisplayName("null inputs are rejected with a clear message, not an NPE")
    void nullInputsAreRejected() {
        assertThatThrownBy(() -> EasyAI.listen().withConfig(withoutKey()).from((java.io.InputStream) null, "audio/webm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stream");

        assertThatThrownBy(() -> EasyAI.listen().withConfig(withoutKey()).from((java.nio.file.Path) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }
}
