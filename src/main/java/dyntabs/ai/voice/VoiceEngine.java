package dyntabs.ai.voice;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiTextToSpeechModel;

/**
 * Bridges EasyAI to LangChain4J's audio models — the voice counterpart of
 * {@link dyntabs.ai.rag.RagEngine} and {@link dyntabs.ai.rag.MilvusEngine}.
 *
 * <h2>What this class actually does</h2>
 * <p>It is the single place that knows about {@link AudioTranscriptionModel} and
 * {@link TextToSpeechModel}. Everything above it ({@code EasyAI.listen()},
 * {@code EasyAI.speak()}) deals only in {@code byte[]}, {@link String} and {@link Speech} — the
 * shapes a Jakarta EE web tier already has in hand. Four small jobs live here:</p>
 * <ol>
 *   <li><b>Build the model</b> from a {@link VoiceConfig} (provider, key, base URL, model name).</li>
 *   <li><b>Wrap the bytes</b> as a LangChain4J {@link Audio} — including the detail that an upload
 *       arrives as a {@code byte[]} while a model may want Base64.</li>
 *   <li><b>Unwrap the answer</b> — a transcript as {@link String}, synthesized audio as
 *       {@link Speech} with its MIME type intact.</li>
 *   <li><b>Fail with a usable message</b> when a key is missing, instead of a provider-level NPE.</li>
 * </ol>
 *
 * <h2>Providers</h2>
 * <p>Only {@code "openai"} is built in, which covers <em>any</em> OpenAI-compatible endpoint via
 * {@link VoiceConfig#baseUrl()} — Groq's Whisper being the obvious example, and the reason voice
 * typically needs no new provider and no new key. Any other provider id fails fast with a message
 * naming what is supported, rather than silently doing something else. New providers plug in here
 * and nowhere else.</p>
 *
 * <p><b>Analogy:</b> the adapter plug for a foreign appliance. The appliance (your web endpoint)
 * keeps its own plain plug; the adapter knows the local socket's shape. Change country and you
 * change the adapter, not the appliance.</p>
 *
 * @see VoiceConfig
 * @see Speech
 * @see dyntabs.ai.EasyAI#listen()
 * @see dyntabs.ai.EasyAI#speak()
 */
public final class VoiceEngine {

    private static final Logger log = LoggerFactory.getLogger(VoiceEngine.class);

    /** What OpenAI-compatible speech synthesis returns by default when it does not say. */
    private static final String DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg";

    private VoiceEngine() {
    }

    /**
     * Transcribe one spoken utterance.
     *
     * @param audio    the recorded bytes (e.g. a WebM/Opus blob from a browser's MediaRecorder)
     * @param mimeType the audio MIME type, e.g. {@code "audio/webm"}; {@code null} is allowed but
     *                 providers generally transcribe more reliably when told
     * @param config   which model to call and how to reach it
     * @return the recognised text (possibly empty if nothing intelligible was heard)
     * @throws IllegalArgumentException if {@code audio} is null or empty
     * @throws IllegalStateException    if no API key is configured
     */
    public static String transcribe(byte[] audio, String mimeType, VoiceConfig config) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Nothing to transcribe: the audio is empty.");
        }
        requireKey(config, "transcription (speech-to-text)", "easyai.stt.api-key");

        AudioTranscriptionRequest.Builder request = AudioTranscriptionRequest.builder()
                .audio(toAudio(audio, mimeType));
        if (config.language() != null) {
            request.language(config.language());
        }

        log.debug("Transcribing {} bytes with {}", audio.length, config);
        String text = transcriptionModel(config).transcribe(request.build()).text();
        return text == null ? "" : text.trim();
    }

    /**
     * Synthesize speech for one piece of text.
     *
     * @param text   the words to speak (required, non-blank)
     * @param config which model and voice to use, and how to reach it
     * @return the audio plus its MIME type
     * @throws IllegalArgumentException if {@code text} is null or blank
     * @throws IllegalStateException    if no API key is configured, or the provider returned no audio
     */
    public static Speech synthesize(String text, VoiceConfig config) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Nothing to speak: the text is blank.");
        }
        requireKey(config, "speech synthesis (text-to-speech)", "easyai.tts.api-key");

        TextToSpeechRequest.Builder request = TextToSpeechRequest.builder().text(text);
        if (config.voice() != null) {
            request.voice(config.voice());
        }

        log.debug("Synthesizing {} characters with {}", text.length(), config);
        TextToSpeechResponse response = speechModel(config).synthesize(request.build());
        return toSpeech(response);
    }

    /**
     * Build the LangChain4J transcription model described by {@code config}.
     *
     * <p>Exposed for callers who want the raw LangChain4J model — the escape hatch, so choosing
     * EasyAI never means losing access to the layer underneath.</p>
     *
     * @param config the voice settings
     * @return a ready {@link AudioTranscriptionModel}
     */
    public static AudioTranscriptionModel transcriptionModel(VoiceConfig config) {
        String provider = providerOf(config);
        if (!"openai".equals(provider)) {
            throw new IllegalArgumentException("Unsupported voice provider for transcription: '" + provider
                    + "'. Built in: 'openai' — which also covers any OpenAI-compatible endpoint "
                    + "(set easyai.stt.base-url, e.g. Groq).");
        }
        OpenAiAudioTranscriptionModel.Builder builder = OpenAiAudioTranscriptionModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.modelName());
        if (config.baseUrl() != null) {
            builder.baseUrl(config.baseUrl());
        }
        return builder.build();
    }

    /**
     * Build the LangChain4J speech-synthesis model described by {@code config}.
     *
     * <p>Exposed as the same escape hatch as {@link #transcriptionModel(VoiceConfig)}.</p>
     *
     * @param config the voice settings
     * @return a ready {@link TextToSpeechModel}
     */
    public static TextToSpeechModel speechModel(VoiceConfig config) {
        String provider = providerOf(config);
        if (!"openai".equals(provider)) {
            throw new IllegalArgumentException("Unsupported voice provider for synthesis: '" + provider
                    + "'. Built in: 'openai' — which also covers any OpenAI-compatible endpoint "
                    + "(set easyai.tts.base-url).");
        }
        OpenAiTextToSpeechModel.Builder builder = OpenAiTextToSpeechModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.modelName());
        if (config.baseUrl() != null) {
            builder.baseUrl(config.baseUrl());
        }
        if (config.voice() != null) {
            builder.voice(config.voice());
        }
        return builder.build();
    }

    /**
     * Wrap raw bytes as a LangChain4J {@link Audio}.
     *
     * <p>Both {@code binaryData} and {@code base64Data} are populated on purpose. Different
     * integrations read different one of the two, and an upload always arrives as bytes — so
     * filling both makes the call work regardless, instead of handing back a confusing empty
     * transcript. This is precisely the sort of plumbing a caller should not have to discover.</p>
     */
    private static Audio toAudio(byte[] audio, String mimeType) {
        Audio.Builder builder = Audio.builder()
                .binaryData(audio)
                .base64Data(Base64.getEncoder().encodeToString(audio));
        if (mimeType != null && !mimeType.isBlank()) {
            builder.mimeType(mimeType.trim());
        }
        return builder.build();
    }

    /** Unwrap a synthesis response into {@link Speech}, tolerating binary-or-Base64 payloads. */
    private static Speech toSpeech(TextToSpeechResponse response) {
        Audio audio = response == null ? null : response.audio();
        if (audio == null) {
            throw new IllegalStateException("Speech synthesis returned no audio.");
        }
        byte[] bytes = audio.binaryData();
        if (bytes == null || bytes.length == 0) {
            String base64 = audio.base64Data();
            if (base64 == null || base64.isBlank()) {
                throw new IllegalStateException("Speech synthesis returned an empty audio payload.");
            }
            bytes = Base64.getDecoder().decode(base64);
        }
        String mimeType = audio.mimeType() != null ? audio.mimeType() : DEFAULT_AUDIO_MIME_TYPE;
        return new Speech(bytes, mimeType);
    }

    private static String providerOf(VoiceConfig config) {
        return config.provider() == null ? "openai" : config.provider().toLowerCase();
    }

    /** Fail early and say which property to set, rather than letting the provider NPE. */
    private static void requireKey(VoiceConfig config, String what, String propertyName) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("No API key configured for " + what
                    + ". Set " + propertyName + " (or easyai.api-key to share the chat key) in easyai.properties, "
                    + "or pass it programmatically.");
        }
    }
}
