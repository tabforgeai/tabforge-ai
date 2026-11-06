package dyntabs.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIListener;
import dyntabs.ai.event.EventEmitter;
import dyntabs.ai.voice.VoiceConfig;
import dyntabs.ai.voice.VoiceEngine;

/**
 * Turns recorded speech into text — the inbound half of the voice channel.
 *
 * <h2>The shortest useful form</h2>
 * <pre>{@code
 * String text = EasyAI.listen().from(audioBytes, "audio/webm");
 * }</pre>
 * <p>That is the whole thing. No model builder, no request object, no Base64, no provider name:
 * the model, key and base URL come from {@code easyai.properties}, and — the point worth knowing —
 * they <b>default to your chat settings</b>. An app that already talks to an OpenAI-compatible
 * provider for chat gains speech input with <em>no new configuration at all</em>
 * (see {@link VoiceConfig}).</p>
 *
 * <h2>Why this exists at all</h2>
 * <p>Voice should be a new <b>channel</b> into the assistant you already have, not a second brain.
 * The transcript this returns is ordinary text, so you hand it to the same
 * {@link EasyAI#assistant(Class) assistant} — and spoken turns inherit your tools, memory and
 * context for free. That is also why this class returns a {@link String} rather than an answer:
 * transcribing and deciding are deliberately separate jobs.</p>
 *
 * <h2>Overriding per call</h2>
 * <pre>{@code
 * String text = EasyAI.listen()
 *                     .withModel("whisper-large-v3")      // e.g. when the base URL points at Groq
 *                     .withLanguage("sr")                 // skip language detection
 *                     .withEventListener(e -> log.info("{}", e))
 *                     .from(audioBytes, "audio/webm");
 * }</pre>
 *
 * <p><b>Analogy:</b> a dictation service. You slide in the recording and get back a typed page.
 * It does not answer the letter — that is the next desk along.</p>
 *
 * @see EasyAI#listen()
 * @see SpeakBuilder
 * @see VoiceConfig
 */
public class ListenBuilder {

    private VoiceConfig config;
    private String modelName;
    private String language;
    private String apiKey;
    private String baseUrl;
    private EasyAIListener eventListener;

    /** Package-private: instances come from {@link EasyAI#listen()}. */
    ListenBuilder() {
    }

    /**
     * Replace the whole configuration instead of overriding single values.
     *
     * @param config fully built voice settings; {@code null} restores properties-based defaults
     * @return this builder
     */
    public ListenBuilder withConfig(VoiceConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Name the transcription model for this call.
     *
     * <p>Needed when the base URL points somewhere other than OpenAI — Groq's Whisper is
     * {@code whisper-large-v3}, OpenAI's default is
     * {@link VoiceConfig#DEFAULT_TRANSCRIPTION_MODEL}. Nothing is guessed from the URL.</p>
     *
     * @param modelName the provider's model id
     * @return this builder
     */
    public ListenBuilder withModel(String modelName) {
        this.modelName = modelName;
        return this;
    }

    /**
     * Tell the model which language to expect, as an ISO-639-1 code (e.g. {@code "sr"}, {@code "en"}).
     *
     * <p>Leave it unset to let the model detect the language — which is what you want for a
     * bilingual audience, and a real advantage over a browser recognizer pinned to one locale.
     * Set it when you know the language and want the accuracy.</p>
     *
     * @param language ISO-639-1 language code
     * @return this builder
     */
    public ListenBuilder withLanguage(String language) {
        this.language = language;
        return this;
    }

    /**
     * Override the API key for this call (otherwise {@code easyai.stt.api-key}, then
     * {@code easyai.api-key}).
     *
     * @param apiKey the provider API key
     * @return this builder
     */
    public ListenBuilder withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Override the base URL for this call (otherwise {@code easyai.stt.base-url}, then
     * {@code easyai.base-url}) — this is how you point transcription at any OpenAI-compatible
     * service.
     *
     * @param baseUrl e.g. {@code https://api.groq.com/openai/v1/}
     * @return this builder
     */
    public ListenBuilder withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Register a listener for the live {@link dyntabs.ai.event.EasyAIEvent} stream, so a
     * transcription shows up in your activity timeline like every other EasyAI operation.
     *
     * <p>Emits {@link dyntabs.ai.event.EasyAIEvent.Phase#STARTED} when the call begins and
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#RESULT} carrying the transcript (or
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#ERROR}), under
     * {@link Source#VOICE}. A UI can therefore show <em>what it heard</em> — which is the one
     * thing users most want to verify about a voice interface.</p>
     *
     * @param eventListener the listener (may be {@code null} to disable events)
     * @return this builder
     */
    public ListenBuilder withEventListener(EasyAIListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    /**
     * Transcribe recorded audio.
     *
     * @param audio    the audio bytes
     * @param mimeType the audio MIME type, e.g. {@code "audio/webm"}, {@code "audio/mpeg"}
     * @return the recognised text, trimmed (empty if nothing intelligible was heard)
     */
    public String from(byte[] audio, String mimeType) {
        VoiceConfig resolved = resolve();
        EventEmitter emitter = new EventEmitter(Source.VOICE, eventListener);
        emitter.started("Transcribing speech");
        try {
            String text = VoiceEngine.transcribe(audio, mimeType, resolved);
            emitter.result("Heard", text);
            return text;
        } catch (RuntimeException e) {
            emitter.error("Transcription failed", EasyAI.extractErrorMessage(e));
            throw e;
        }
    }

    /**
     * Transcribe recorded audio whose MIME type you do not have at hand.
     *
     * @param audio the audio bytes
     * @return the recognised text
     */
    public String from(byte[] audio) {
        return from(audio, null);
    }

    /**
     * Transcribe audio read from a stream — the shape a servlet or JAX-RS multipart part gives you.
     *
     * @param audio    the stream to read fully (not closed by this method)
     * @param mimeType the audio MIME type, e.g. the part's declared type
     * @return the recognised text
     * @throws IllegalStateException if the stream cannot be read
     */
    public String from(InputStream audio, String mimeType) {
        if (audio == null) {
            throw new IllegalArgumentException("Nothing to transcribe: the audio stream is null.");
        }
        try {
            return from(audio.readAllBytes(), mimeType);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the audio stream: " + e.getMessage(), e);
        }
    }

    /**
     * Transcribe an audio file, detecting its MIME type from the file itself.
     *
     * @param audioFile path to the recording
     * @return the recognised text
     * @throws IllegalStateException if the file cannot be read
     */
    public String from(Path audioFile) {
        if (audioFile == null) {
            throw new IllegalArgumentException("Nothing to transcribe: the audio path is null.");
        }
        try {
            String probed = Files.probeContentType(audioFile);
            return from(Files.readAllBytes(audioFile), probed);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the audio file " + audioFile + ": " + e.getMessage(), e);
        }
    }

    /** Start from properties (or the supplied config) and lay the per-call overrides on top. */
    private VoiceConfig resolve() {
        VoiceConfig base = config != null ? config : VoiceConfig.forListening();
        VoiceConfig.Builder builder = base.toBuilder();
        if (modelName != null) {
            builder.modelName(modelName);
        }
        if (language != null) {
            builder.language(language);
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
