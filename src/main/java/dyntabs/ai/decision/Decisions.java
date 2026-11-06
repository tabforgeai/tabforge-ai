package dyntabs.ai.decision;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The answers from one {@code EasyAI.decide(...)} call — one entry per question you asked.
 *
 * <h2>Why this wrapper exists (it is not cosmetic)</h2>
 * <p>The underlying answers carry a real trap: <b>probabilities are optional</b>. When the model
 * does not report them, the raw {@code probabilityOf(...)} and {@code margin()} accessors
 * <em>throw</em> {@link IllegalStateException}, and {@code confidence()} can be {@code null}. Code
 * written against the happy path looks correct, passes review, and fails in production on the first
 * answer that arrives without probabilities.</p>
 * <p>So every accessor here that <em>can</em> be absent returns {@link OptionalDouble} instead of
 * throwing, and the ones that are always present return a plain {@code double}. The type tells you
 * which is which, at compile time.</p>
 *
 * <h2>Thresholds are deliberate, never defaulted</h2>
 * <p>There is no {@code isYes(name)} — only {@link #isYes(String, double)}. A yes/no answer is a
 * probability, and turning a probability into a decision means choosing a line. That line is a
 * business judgement, so the API makes you write it down where a reviewer can see it. "A measured
 * probability crossed a threshold I chose" is a defensible sentence; "the AI decided" is not.</p>
 *
 * <h2>Mistakes are named, not cryptic</h2>
 * <p>Asking for a question you never asked, or reading a choice answer as yes/no, fails with a
 * message that names the question and lists what is actually available — rather than a
 * {@link ClassCastException} or a {@code null}.</p>
 *
 * <pre>{@code
 * Decisions d = EasyAI.decide(ticketText)
 *     .yesNo("urgent",   "Is the customer blocked from working?")
 *     .choice("team",    "Which team should handle this?", "billing", "technical", "sales")
 *     .scale("severity", "How severe is the impact?", "low", "medium", "high")
 *     .ask();
 *
 * if (d.isYes("urgent", 0.80)) pager.wake();        // the 0.80 is YOUR call, in the open
 * route(d.choice("team"));
 * double severity = d.mean("severity");             // 0.0 .. levels-1, can land between levels
 *
 * d.margin("team").ifPresent(m -> { if (m < 0.15) queueForHuman(); });   // only if reported
 * }</pre>
 *
 * <p><b>Analogy:</b> a lab report. It states each measurement and its confidence; it does not tell
 * you whether to operate. The threshold for acting is the clinician's, written in the notes.</p>
 *
 * @see dyntabs.ai.EasyAI#decide(String)
 */
public final class Decisions {

    private final DecisionResponse response;

    /**
     * @param response the raw provider response (required)
     */
    Decisions(DecisionResponse response) {
        if (response == null) {
            throw new IllegalStateException("The decision model returned no response.");
        }
        this.response = response;
    }

    /**
     * Turn a yes/no probability into a decision at <b>your</b> threshold.
     *
     * @param name      the question name you used when asking
     * @param threshold the probability at or above which this counts as "yes", between 0.0 and 1.0
     * @return {@code true} if the reported probability reaches {@code threshold}
     * @throws IllegalArgumentException if {@code threshold} is outside 0.0–1.0, or the question is
     *                                  unknown or was not a yes/no question
     */
    public boolean isYes(String name, double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0 but was " + threshold
                    + ". It is a probability, not a percentage.");
        }
        return yesNo(name).isYes(threshold);
    }

    /**
     * The raw probability of "yes" for a yes/no question — useful for logging the number you
     * actually decided on, or for feeding a gauge.
     *
     * @param name the question name
     * @return the probability of "yes", 0.0–1.0
     */
    public double probability(String name) {
        return yesNo(name).probability();
    }

    /**
     * The option the model selected for a choice question.
     *
     * @param name the question name
     * @return the chosen option, exactly as you spelled it when asking
     */
    public String choice(String name) {
        return choiceAnswer(name).value();
    }

    /**
     * How likely a specific option was, if the model reported per-option probabilities.
     *
     * @param name   the question name
     * @param option one of the options you offered
     * @return the probability, or {@link OptionalDouble#empty()} if the model reported none
     */
    public OptionalDouble probabilityOf(String name, String option) {
        ChoiceAnswer answer = choiceAnswer(name);
        if (answer.probabilities() == null || answer.probabilities().isEmpty()) {
            return OptionalDouble.empty();
        }
        Double value = answer.probabilities().get(option);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /**
     * All per-option probabilities for a choice question.
     *
     * @param name the question name
     * @return the probabilities, or an empty map if the model reported none (never {@code null})
     */
    public Map<String, Double> probabilities(String name) {
        Map<String, Double> p = choiceAnswer(name).probabilities();
        return p == null ? Collections.emptyMap() : p;
    }

    /**
     * The gap between the winning option and the runner-up — the cleanest signal of "the model is
     * unsure, send this to a human".
     *
     * @param name the question name
     * @return the margin, or {@link OptionalDouble#empty()} if no probabilities were reported
     */
    public OptionalDouble margin(String name) {
        ChoiceAnswer answer = choiceAnswer(name);
        if (answer.probabilities() == null || answer.probabilities().isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(answer.margin());
    }

    /**
     * The model's own confidence in a choice or scale answer, when it reports one.
     *
     * @param name the question name
     * @return the confidence, or {@link OptionalDouble#empty()} if the model did not report it
     */
    public OptionalDouble confidence(String name) {
        DecisionAnswer answer = require(name);
        Double confidence = switch (answer) {
            case ChoiceAnswer c -> c.confidence();
            case ScaleAnswer s -> s.confidence();
            default -> null;
        };
        return confidence == null ? OptionalDouble.empty() : OptionalDouble.of(confidence);
    }

    /**
     * The position of a scale answer across the levels you offered, as a probability-weighted index.
     *
     * <p>It can land <em>between</em> two levels — with levels {@code low, medium, high} a mean of
     * {@code 1.4} means "somewhere between medium and high, closer to medium". That in-between value
     * is the point of a scale question; rounding it away throws the information out.</p>
     *
     * @param name the question name
     * @return the weighted index, from 0.0 to {@code levels - 1}
     */
    public double mean(String name) {
        DecisionAnswer answer = require(name);
        if (answer instanceof ScaleAnswer scale) {
            return scale.mean();
        }
        throw typeMismatch(name, answer, "a scale");
    }

    /**
     * Whether the model declined to answer a particular question.
     *
     * <p>Check this before reading an answer when a question could touch content the model refuses
     * to judge; reading a refused answer is an error, not an empty result.</p>
     *
     * @param name the question name
     * @return {@code true} if the model refused this question
     */
    public boolean isRefused(String name) {
        return response.isRefused(name);
    }

    /** @return the names of every question answered, in the order the provider returned them. */
    public Set<String> names() {
        return response.answers().keySet();
    }

    /** @return the model that answered, as reported by the provider. */
    public String modelName() {
        return response.modelName();
    }

    /** @return token usage for the call, for cost accounting (may be {@code null}). */
    public TokenUsage tokenUsage() {
        return response.tokenUsage();
    }

    /**
     * The raw LangChain4J response — the escape hatch, so using EasyAI never means losing access to
     * the layer underneath.
     *
     * @return the underlying response
     */
    public DecisionResponse raw() {
        return response;
    }

    /**
     * A short, readable line per answer — what gets logged or shown in an activity timeline.
     *
     * <p>Deliberately includes the <em>numbers</em>, not just the verdicts: an audit trail that says
     * {@code urgent=0.91} is reviewable, one that says {@code urgent=yes} is not.</p>
     *
     * @return e.g. {@code "urgent=0.91, team=technical (margin 0.42), severity=1.40"}
     */
    public String summary() {
        return response.answers().entrySet().stream()
                .map(e -> e.getKey() + "=" + describe(e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return "Decisions[" + summary() + "]";
    }

    private String describe(String name, DecisionAnswer answer) {
        if (response.isRefused(name)) {
            return "REFUSED";
        }
        // Locale.ROOT on purpose: this string goes into logs and audit trails, where "0.91" must not
        // become "0,91" just because the server runs with a comma-decimal locale.
        return switch (answer) {
            case YesNoAnswer y -> String.format(Locale.ROOT, "%.2f", y.probability());
            case ChoiceAnswer c -> c.value()
                    + (c.probabilities() == null || c.probabilities().isEmpty()
                            ? "" : String.format(Locale.ROOT, " (margin %.2f)", c.margin()));
            case ScaleAnswer s -> String.format(Locale.ROOT, "%.2f", s.mean());
            default -> String.valueOf(answer);
        };
    }

    /** Fetch a yes/no answer, or explain precisely what went wrong. */
    private YesNoAnswer yesNo(String name) {
        DecisionAnswer answer = require(name);
        if (answer instanceof YesNoAnswer yes) {
            return yes;
        }
        throw typeMismatch(name, answer, "a yes/no");
    }

    /** Fetch a choice answer, or explain precisely what went wrong. */
    private ChoiceAnswer choiceAnswer(String name) {
        DecisionAnswer answer = require(name);
        if (answer instanceof ChoiceAnswer choice) {
            return choice;
        }
        throw typeMismatch(name, answer, "a choice");
    }

    /**
     * Look up an answer by question name, failing with the list of names that DO exist — the fastest
     * possible fix for the most common mistake, a typo in the question name.
     */
    private DecisionAnswer require(String name) {
        Map<String, DecisionAnswer> answers = new LinkedHashMap<>(response.answers());
        DecisionAnswer answer = answers.get(name);
        if (answer == null) {
            throw new IllegalArgumentException("No decision answer named '" + name
                    + "'. Questions answered: " + answers.keySet() + ".");
        }
        if (response.isRefused(name)) {
            throw new IllegalStateException("The model refused to answer '" + name
                    + "'. Call isRefused(\"" + name + "\") before reading it.");
        }
        return answer;
    }

    private static IllegalArgumentException typeMismatch(String name, DecisionAnswer answer, String expected) {
        return new IllegalArgumentException("Question '" + name + "' was not " + expected
                + " question; it answered with " + answer.getClass().getSimpleName()
                + ". Read it with the matching accessor.");
    }
}
