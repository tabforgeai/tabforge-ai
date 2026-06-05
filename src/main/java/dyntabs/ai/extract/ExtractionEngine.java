package dyntabs.ai.extract;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSyntaxException;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EventEmitter;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The extraction assembly line: prompt the model with a JSON skeleton, read its answer,
 * parse it into the target type, and (optionally) validate it — retrying if the model's
 * first answer is not valid JSON.
 *
 * <p><b>Analogy:</b> {@link dyntabs.ai.rag.RagEngine} is to retrieval what
 * {@code ExtractionEngine} is to structured output — the low-level worker that
 * {@link dyntabs.ai.ExtractionBuilder} delegates to once the caller has chosen a type and
 * options. Think of it as a factory line: {@link SchemaDescriber} stamps the blank form,
 * the model fills it in, Gson presses it into a Java object, and a quality-control step
 * (retry + optional Bean Validation) rejects defective parts.</p>
 *
 * <p>It is provider-agnostic: instead of relying on a provider-specific JSON response mode,
 * it instructs the model to emit JSON and then tolerantly extracts the JSON object from the
 * reply (stripping markdown fences or stray prose). This keeps it working across OpenAI,
 * Groq, Ollama, and any other LangChain4J {@link ChatModel}.</p>
 *
 * @see dyntabs.ai.ExtractionBuilder
 * @see SchemaDescriber
 */
public final class ExtractionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEngine.class);

    /** Gson configured to parse the {@code java.time} types EasyAI advertises in the schema. */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class,
                    (JsonDeserializer<LocalDate>) (j, t, c) -> LocalDate.parse(j.getAsString()))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (j, t, c) -> LocalDateTime.parse(j.getAsString()))
            .registerTypeAdapter(LocalTime.class,
                    (JsonDeserializer<LocalTime>) (j, t, c) -> LocalTime.parse(j.getAsString()))
            .registerTypeAdapter(Instant.class,
                    (JsonDeserializer<Instant>) (j, t, c) -> Instant.parse(j.getAsString()))
            .create();

    /** Lazily built Bean Validation validator; null until first use or if no provider exists. */
    private static volatile Validator validator;

    private ExtractionEngine() {
    }

    /**
     * Runs the full extraction for one piece of content.
     *
     * <p>Called by {@link dyntabs.ai.ExtractionBuilder#from(String)} after the builder has
     * resolved the model and options.</p>
     *
     * @param model      the chat model to query (real or a test mock)
     * @param type       the class to extract (record or POJO)
     * @param content    the source text to extract from
     * @param maxRetries how many additional attempts to make if the model returns
     *                   unparseable JSON (0 = a single attempt)
     * @param validate   whether to run Jakarta Bean Validation on the result
     * @param <T>        the target type
     * @return a populated instance of {@code type}
     * @throws ExtractionException if no valid JSON could be parsed within the retries, or if
     *                             validation is enabled and the result is invalid
     */
    public static <T> T extract(ChatModel model, Class<T> type, String content,
                                int maxRetries, boolean validate) {
        return extract(model, type, content, maxRetries, validate,
                new EventEmitter(Source.EXTRACT, null));
    }

    /**
     * Same as {@link #extract(ChatModel, Class, String, int, boolean)}, but additionally narrates
     * its progress to the given {@link EventEmitter}.
     *
     * <p>Called by {@link dyntabs.ai.ExtractionBuilder#from(String)}. Emits a STARTED event up
     * front, a PROGRESS event when the model is queried, a RETRY event for each re-attempt on
     * malformed JSON, and a terminal RESULT (success) or ERROR event. The emitter is a no-op when
     * no listener was registered, so this path is free when nobody is observing.</p>
     *
     * @param model      the chat model to query (real or a test mock)
     * @param type       the class to extract (record or POJO)
     * @param content    the source text to extract from
     * @param maxRetries how many additional attempts on unparseable JSON (0 = a single attempt)
     * @param validate   whether to run Jakarta Bean Validation on the result
     * @param emitter    the live-event emitter (never {@code null}; pass a no-op emitter to disable)
     * @param <T>        the target type
     * @return a populated instance of {@code type}
     * @throws ExtractionException if no valid JSON could be parsed within the retries, or if
     *                             validation is enabled and the result is invalid
     */
    public static <T> T extract(ChatModel model, Class<T> type, String content,
                                int maxRetries, boolean validate, EventEmitter emitter) {
        String schema = SchemaDescriber.describe(type);
        String systemPrompt = buildSystemPrompt(schema);

        emitter.started("Extracting " + type.getSimpleName());

        String lastRaw = null;
        JsonSyntaxException lastError = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(content));
            if (attempt > 0 && lastRaw != null) {
                emitter.retry("Retrying extraction",
                        "attempt " + (attempt + 1) + " — previous output was not valid JSON");
                messages.add(UserMessage.from(
                        "Your previous answer was not valid JSON for the schema. "
                        + "Return ONLY the JSON object, nothing else. Previous answer was:\n" + lastRaw));
            } else {
                emitter.progress("Querying model", "target type: " + type.getSimpleName());
            }

            ChatRequest request = ChatRequest.builder().messages(messages).build();
            lastRaw = model.chat(request).aiMessage().text();

            try {
                T result = GSON.fromJson(extractJsonObject(lastRaw), type);
                if (result == null) {
                    throw new JsonSyntaxException("Model returned JSON null");
                }
                if (validate) {
                    try {
                        validate(result);
                    } catch (ExtractionException ve) {
                        emitter.error("Validation failed", ve.getMessage());
                        throw ve;
                    }
                }
                emitter.result("Extracted " + type.getSimpleName(), null);
                return result;
            } catch (JsonSyntaxException e) {
                lastError = e;
                log.debug("Extraction attempt {} produced unparseable JSON: {}", attempt + 1, e.getMessage());
            }
        }

        emitter.error("Extraction failed",
                "no valid JSON after " + (maxRetries + 1) + " attempt(s)");
        throw new ExtractionException(
                "Could not extract " + type.getSimpleName() + " after " + (maxRetries + 1)
                + " attempt(s). Last model output was:\n" + lastRaw, lastError);
    }

    /**
     * Builds the system prompt that pins the model to the target schema.
     *
     * @param schema the JSON skeleton from {@link SchemaDescriber}
     * @return the system instruction
     */
    private static String buildSystemPrompt(String schema) {
        return "You are a precise data-extraction engine. Read the user's content and "
                + "extract the requested information.\n\n"
                + "Return ONLY a single JSON object that conforms exactly to this structure "
                + "(use these field names and types). Do not wrap it in markdown, do not add "
                + "any explanation.\n\nSchema:\n" + schema + "\n\nRules:\n"
                + "- Use null for any field whose value is not present in the content.\n"
                + "- Dates and times must be ISO-8601 strings.\n"
                + "- Numbers must be plain JSON numbers (no currency symbols or thousand separators).\n"
                + "- The output must be valid JSON, parseable as-is.";
    }

    /**
     * Tolerantly pulls the JSON object out of a model reply: strips Markdown code fences and
     * any leading/trailing prose by taking the text from the first {@code &#123;} to the last
     * {@code &#125;}.
     *
     * @param raw the raw model reply
     * @return the candidate JSON object string (or the trimmed input if no braces are found)
     */
    private static String extractJsonObject(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            // strip ```json ... ``` fences
            text = text.replaceAll("(?s)```(?:json)?", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * Runs Jakarta Bean Validation against an extracted object and throws if it is invalid.
     *
     * @param object the extracted instance
     * @throws ExtractionException if any constraint is violated, or if no Bean Validation
     *                             provider is on the classpath
     */
    private static void validate(Object object) {
        Validator v = obtainValidator();
        Set<ConstraintViolation<Object>> violations = v.validate(object);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Extracted ")
                    .append(object.getClass().getSimpleName())
                    .append(" failed validation:");
            for (ConstraintViolation<Object> cv : violations) {
                sb.append("\n - ").append(cv.getPropertyPath()).append(' ').append(cv.getMessage());
            }
            throw new ExtractionException(sb.toString());
        }
    }

    /**
     * Lazily obtains (and caches) the default Bean Validation {@link Validator}.
     *
     * @return the shared validator
     * @throws ExtractionException if no Bean Validation provider (e.g. Hibernate Validator)
     *                             is available on the classpath
     */
    private static Validator obtainValidator() {
        Validator local = validator;
        if (local == null) {
            synchronized (ExtractionEngine.class) {
                local = validator;
                if (local == null) {
                    try {
                        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
                        local = factory.getValidator();
                        validator = local;
                    } catch (RuntimeException e) {
                        throw new ExtractionException(
                                "validate() was requested but no Jakarta Bean Validation provider "
                                + "(e.g. Hibernate Validator) is on the classpath.", e);
                    }
                }
            }
        }
        return local;
    }
}
