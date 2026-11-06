package dyntabs.ai;

import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIListener;
import dyntabs.ai.event.EventEmitter;
import dyntabs.ai.voice.Speech;
import dyntabs.ai.voice.VoiceConfig;
import dyntabs.ai.voice.VoiceEngine;

/**
 * Turns text into spoken audio — the outbound half of the voice channel.
 *
 * <h2>The shortest useful form</h2>
 * <pre>{@code
 * Speech speech = EasyAI.speak().from("Order ORD-004 has been delivered.");
 * return Response.ok(speech.bytes()).type(speech.mimeType()).build();
 * }</pre>
 * <p>Model, voice, key and base URL come from {@code easyai.properties}, defaulting to your chat
 * settings — so, as with {@link ListenBuilder}, the common case needs no new configuration
 * (see {@link VoiceConfig}).</p>
 *
 * <h2>Server-side speech vs. the browser's own</h2>
 * <p>A browser can already speak text for free via the Web Speech API, and for a desktop app that
 * is often the right answer. Synthesizing on the server earns its keep when you need a
 * <em>consistent</em> voice across browsers and devices, a language the client has no voice
 * installed for, or audio you can store, attach and replay — a recorded confirmation, an
 * accessibility narration, an outbound notification. This is the path for those; it returns the
 * bytes and leaves playback entirely up to you.</p>
 *
 * <h2>Overriding per call</h2>
 * <pre>{@code
 * Speech speech = EasyAI.speak()
 *                       .withModel("tts-1-hd")
 *                       .withVoice("nova")
 *                       .withEventListener(e -> log.info("{}", e))
 *                       .from(answer);
 * }</pre>
 *
 * <p><b>Analogy:</b> a voice-over artist. You hand over the script and get back a recording — the
 * artist neither writes the script nor decides when it is played.</p>
 *
 * @see EasyAI#speak()
 * @see ListenBuilder
 * @see Speech
 * @see VoiceConfig
 */
public class SpeakBuilder {

    private VoiceConfig config;
    private String modelName;
    private String voice;
    private String apiKey;
    private String baseUrl;
    private EasyAIListener eventListener;

    /** Package-private: instances come from {@link EasyAI#speak()}. */
    SpeakBuilder() {
    }

    /**
     * Replace the whole configuration instead of overriding single values.
     *
     * @param config fully built voice settings; {@code null} restores properties-based defaults
     * @return this builder
     */
    public SpeakBuilder withConfig(VoiceConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Name the synthesis model for this call (default {@link VoiceConfig#DEFAULT_SPEECH_MODEL}).
     *
     * @param modelName the provider's model id, e.g. {@code "tts-1"}, {@code "tts-1-hd"}
     * @return this builder
     */
    public SpeakBuilder withModel(String modelName) {
        this.modelName = modelName;
        return this;
    }

    /**
     * Choose the voice (default {@link VoiceConfig#DEFAULT_VOICE}).
     *
     * @param voice the provider's voice name, e.g. {@code "alloy"}, {@code "nova"}
     * @return this builder
     */
    public SpeakBuilder withVoice(String voice) {
        this.voice = voice;
        return this;
    }

    /**
     * Override the API key for this call (otherwise {@code easyai.tts.api-key}, then
     * {@code easyai.api-key}).
     *
     * @param apiKey the provider API key
     * @return this builder
     */
    public SpeakBuilder withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Override the base URL for this call (otherwise {@code easyai.tts.base-url}, then
     * {@code easyai.base-url}).
     *
     * @param baseUrl the OpenAI-compatible endpoint to synthesize with
     * @return this builder
     */
    public SpeakBuilder withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Register a listener for the live {@link dyntabs.ai.event.EasyAIEvent} stream, so synthesis
     * appears in your activity timeline alongside every other EasyAI operation.
     *
     * <p>Emits {@link dyntabs.ai.event.EasyAIEvent.Phase#STARTED} and then
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#RESULT} (or
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#ERROR}) under {@link Source#VOICE}.</p>
     *
     * @param eventListener the listener (may be {@code null} to disable events)
     * @return this builder
     */
    public SpeakBuilder withEventListener(EasyAIListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    /**
     * Synthesize speech for the given text.
     *
     * @param text the words to speak
     * @return the audio together with its MIME type
     */
    public Speech from(String text) {
        VoiceConfig resolved = resolve();
        EventEmitter emitter = new EventEmitter(Source.VOICE, eventListener);
        emitter.started("Synthesizing speech");
        try {
            Speech speech = VoiceEngine.synthesize(text, resolved);
            emitter.result("Spoke", speech.size() + " bytes, " + speech.mimeType());
            return speech;
        } catch (RuntimeException e) {
            emitter.error("Speech synthesis failed", EasyAI.extractErrorMessage(e));
            throw e;
        }
    }

    /** Start from properties (or the supplied config) and lay the per-call overrides on top. */
    private VoiceConfig resolve() {
        VoiceConfig base = config != null ? config : VoiceConfig.forSpeaking();
        VoiceConfig.Builder builder = base.toBuilder();
        if (modelName != null) {
            builder.modelName(modelName);
        }
        if (voice != null) {
            builder.voice(voice);
        }
        if (apiKey != null) {
            builder.apiKey(apiKey);
        }
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
