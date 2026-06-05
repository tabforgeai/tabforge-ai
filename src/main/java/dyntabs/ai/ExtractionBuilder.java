package dyntabs.ai;

import java.util.List;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.ChatModel;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIListener;
import dyntabs.ai.event.EventEmitter;
import dyntabs.ai.extract.ExtractionEngine;
import dyntabs.ai.extract.ExtractionException;
import dyntabs.ai.rag.DocumentSource;
import dyntabs.ai.rag.RagEngine;

/**
 * Builds a one-shot, typed extraction: turn unstructured text or a document into a populated
 * Java object (record or POJO).
 *
 * <p><b>Analogy:</b> this is the bridge from the "AI / unstructured" world into your normal
 * typed-Java world. After {@code .from(...)} returns, no AI is involved any more — you hold a
 * plain {@code Invoice}, {@code Order}, or {@code Candidate} that your existing code,
 * JPA entities, EJBs, and PrimeFaces forms already know how to handle.</p>
 *
 * <p>You never construct this directly — start from {@link EasyAI#extract(Class)}:</p>
 * <pre>{@code
 * record Invoice(String vendor, String invoiceNumber, java.time.LocalDate date,
 *                java.math.BigDecimal total, java.util.List<LineItem> items) {}
 *
 * // From free text (an email body, a chat message, a description)
 * Invoice inv = EasyAI.extract(Invoice.class).from(emailBody);
 *
 * // From a document's bytes - parses (Tika) AND extracts in one call
 * Invoice inv = EasyAI.extract(Invoice.class)
 *                     .from(DocumentSource.of("invoice.pdf", pdfBytes));
 *
 * // Then it is just data:
 * em.persist(inv);
 * if (inv.total().compareTo(LIMIT) > 0) approvalService.require(inv);
 * }</pre>
 *
 * <p>Robustness is built in: if the model returns malformed JSON, the extraction retries
 * (see {@link #withRetries(int)}); enable {@link #validate()} to additionally run Jakarta
 * Bean Validation on the result.</p>
 *
 * @param <T> the type to extract
 * @see EasyAI#extract(Class)
 * @see dyntabs.ai.extract.ExtractionEngine
 */
public class ExtractionBuilder<T> {

    private static final int DEFAULT_RETRIES = 2;

    private final Class<T> type;
    private final EasyAIConfig.Builder configOverrides = EasyAIConfig.builder();
    private ChatModel externalModel;
    private int maxRetries = DEFAULT_RETRIES;
    private boolean validate = false;
    private EasyAIListener eventListener;

    /**
     * Package-private: instances come from {@link EasyAI#extract(Class)}. Extraction defaults
     * to temperature 0.0 (deterministic) so the same content yields the same structure.
     *
     * @param type the class to extract
     */
    ExtractionBuilder(Class<T> type) {
        this.type = type;
        this.configOverrides.temperature(0.0);
    }

    /**
     * Overrides the model name for this extraction (e.g. {@code "gpt-4o"}, {@code "llama3"}).
     *
     * @param modelName the model name
     * @return this builder
     */
    public ExtractionBuilder<T> withModel(String modelName) {
        this.configOverrides.modelName(modelName);
        return this;
    }

    /**
     * Overrides the API key for this extraction.
     *
     * @param apiKey the API key
     * @return this builder
     */
    public ExtractionBuilder<T> withApiKey(String apiKey) {
        this.configOverrides.apiKey(apiKey);
        return this;
    }

    /**
     * Overrides the provider ({@code "openai"} or {@code "ollama"}) for this extraction.
     *
     * @param provider the provider name
     * @return this builder
     */
    public ExtractionBuilder<T> withProvider(String provider) {
        this.configOverrides.provider(provider);
        return this;
    }

    /**
     * Overrides the API base URL (proxies, Azure OpenAI, self-hosted endpoints).
     *
     * @param baseUrl the base URL
     * @return this builder
     */
    public ExtractionBuilder<T> withBaseUrl(String baseUrl) {
        this.configOverrides.baseUrl(baseUrl);
        return this;
    }

    /**
     * Overrides the sampling temperature. Extraction defaults to {@code 0.0} (deterministic);
     * raise it only if you have a reason to.
     *
     * @param temperature value between 0.0 and 1.0
     * @return this builder
     */
    public ExtractionBuilder<T> withTemperature(double temperature) {
        this.configOverrides.temperature(temperature);
        return this;
    }

    /**
     * Sets how many additional attempts to make if the model returns malformed JSON.
     *
     * <p>The default is {@value #DEFAULT_RETRIES} (so up to three calls in total). Set 0 to
     * disable retrying.</p>
     *
     * @param retries number of retries on unparseable output (must be &gt;= 0)
     * @return this builder
     */
    public ExtractionBuilder<T> withRetries(int retries) {
        this.maxRetries = Math.max(0, retries);
        return this;
    }

    /**
     * Enables Jakarta Bean Validation on the extracted object.
     *
     * <p>When enabled, constraints such as {@code @NotNull}, {@code @Size}, or {@code @Min}
     * declared on the target type are checked after extraction; a violation throws
     * {@link ExtractionException}. Requires a Bean Validation provider (e.g. Hibernate
     * Validator) on the classpath — present by default in a Jakarta EE container.</p>
     *
     * @return this builder
     */
    public ExtractionBuilder<T> validate() {
        this.validate = true;
        return this;
    }

    /**
     * Injects an externally created {@link ChatModel}, bypassing {@code easyai.properties}
     * and {@code EasyAI.configure()}. Mainly for testing with a mock model.
     *
     * @param model a pre-built ChatModel instance
     * @return this builder
     */
    public ExtractionBuilder<T> withChatModel(ChatModel model) {
        this.externalModel = model;
        return this;
    }

    /**
     * Registers a listener that receives a live {@link dyntabs.ai.event.EasyAIEvent} stream as the
     * extraction runs: {@link dyntabs.ai.event.EasyAIEvent.Phase#STARTED} when it begins, a
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#PROGRESS} when the model is queried, a
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#RETRY} for each re-attempt on malformed JSON, and
     * a final {@link dyntabs.ai.event.EasyAIEvent.Phase#RESULT} (or
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#ERROR}).
     *
     * <p><b>Familiar analogy:</b> a "your form is being processed" status bar — you see it parse,
     * stumble, retry, and finally hand you the finished, typed object.</p>
     *
     * @param eventListener the listener to receive extraction events (may be {@code null})
     * @return this builder
     * @see dyntabs.ai.event.EasyAIListener
     */
    public ExtractionBuilder<T> withEventListener(EasyAIListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    /**
     * Extracts the target type from a plain text string.
     *
     * <p>Terminal step of the {@code EasyAI.extract(Type.class).from(...)} chain. Resolves the
     * model, then delegates to
     * {@link ExtractionEngine#extract(ChatModel, Class, String, int, boolean)}.</p>
     *
     * @param text the source content (email body, message, description, etc.)
     * @return a populated instance of the target type
     * @throws ExtractionException if extraction (or validation, if enabled) fails
     */
    public T from(String text) {
        ChatModel model = externalModel != null
                ? externalModel
                : ModelFactory.create(effectiveConfig());
        EventEmitter emitter = new EventEmitter(Source.EXTRACT, eventListener);
        return ExtractionEngine.extract(model, type, text, maxRetries, validate, emitter);
    }

    /**
     * Extracts the target type directly from a document's bytes.
     *
     * <p>Parses the document (PDF, DOCX, TXT, ... via Apache Tika) into text using
     * {@link RagEngine#parseDocumentSources(List)} and then extracts from that text — so
     * parsing and extraction happen in a single call. Ideal for a PDF/DOCX pulled from a DMS,
     * a database BLOB, or a user upload.</p>
     *
     * @param source the document content + file name (extension drives parsing)
     * @return a populated instance of the target type
     * @throws ExtractionException if the document yields no text, or extraction/validation fails
     */
    public T from(DocumentSource source) {
        List<Document> documents = RagEngine.parseDocumentSources(List.of(source));
        if (documents.isEmpty()) {
            throw new ExtractionException(
                    "Could not parse any text from document '" + source.fileName() + "'");
        }
        String text = documents.stream()
                .map(Document::text)
                .collect(Collectors.joining("\n\n"));
        return from(text);
    }

    // Priority: easyai.properties < EasyAI.configure() < builder .withXxx()
    private EasyAIConfig effectiveConfig() {
        EasyAIConfig base = EasyAI.getGlobalConfig();
        return EasyAIConfigLoader.applyOverrides(base, configOverrides.build());
    }
}
