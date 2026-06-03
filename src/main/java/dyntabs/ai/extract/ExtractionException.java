package dyntabs.ai.extract;

/**
 * Thrown when EasyAI cannot turn the source content into the requested type.
 *
 * <p><b>Analogy:</b> like a {@code NumberFormatException} for AI extraction — the input
 * could not be coerced into the shape you asked for. It is unchecked so it does not clutter
 * call sites, but it carries a clear message and (where relevant) the model's raw output so
 * you can see what went wrong.</p>
 *
 * <p>Raised by {@link dyntabs.ai.ExtractionBuilder#from(String)} (and its overloads) in two
 * situations:</p>
 * <ul>
 *   <li>the model never produced parseable JSON matching the target type, even after the
 *       configured retries;</li>
 *   <li>{@code .validate()} was enabled and the extracted object failed Jakarta Bean
 *       Validation.</li>
 * </ul>
 *
 * @see dyntabs.ai.ExtractionBuilder
 * @see ExtractionEngine
 */
public class ExtractionException extends RuntimeException {

    /**
     * Creates an exception with a human-readable message.
     *
     * @param message what went wrong (and, where useful, the model's raw output)
     */
    public ExtractionException(String message) {
        super(message);
    }

    /**
     * Creates an exception wrapping an underlying cause (e.g. a JSON parse error).
     *
     * @param message what went wrong
     * @param cause   the underlying exception
     */
    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
