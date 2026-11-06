package dyntabs.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dyntabs.ai.decision.DecisionConfig;
import dyntabs.ai.decision.DecisionEngine;
import dyntabs.ai.decision.Decisions;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIListener;
import dyntabs.ai.event.EventEmitter;

/**
 * Asks a small, cheap model a <b>closed question</b> about some text and gets back a
 * <b>probability</b> — not prose.
 *
 * <h2>What this replaces</h2>
 * <p>Routing, triage, classification and gating are usually done with a full chat completion and a
 * structured-output prompt. That is slow, costs a whole generation, and returns a <em>word</em> when
 * what you needed was a <em>number you can threshold</em>. A decision call returns the number:</p>
 *
 * <pre>{@code
 * // one question, no names, no response object to unpack
 * if (EasyAI.decide(ticketText).isYes("Is the customer completely blocked?", 0.80)) {
 *     pager.wake();
 * }
 *
 * String team = EasyAI.decide(ticketText)
 *                     .choose("Which team should handle this?", "billing", "technical", "sales");
 * }</pre>
 *
 * <h2>Several questions, one call</h2>
 * <p>The cost win is asking everything at once. Name each question, then read the answers back by
 * that name:</p>
 * <pre>{@code
 * Decisions d = EasyAI.decide(ticketText)
 *     .yesNo("urgent",   "Is the customer completely blocked?")
 *     .choice("team",    "Which team should handle this?", "billing", "technical", "sales")
 *     .scale("severity", "How severe is the business impact?", "low", "medium", "high")
 *     .ask();
 *
 * if (d.isYes("urgent", 0.80)) pager.wake();
 * route(d.choice("team"));
 * d.margin("team").ifPresent(m -> { if (m < 0.15) queueForHuman(); });
 * }</pre>
 *
 * <h2>Why this fits the rest of EasyAI</h2>
 * <p>It is the same principle as {@link EasyAI#flow()}: <b>the deterministic spine stays in Java,
 * the model is only a sensor</b>. A decision slots straight into a flow's condition, so a pipeline
 * can branch on a measured probability while remaining auditable — the model supplies the reading,
 * your Java supplies the threshold:</p>
 * <pre>{@code
 * EasyAI.flow()
 *     .step("read", ctx -> ticketText)
 *     .stepIf("escalate",
 *             ctx -> EasyAI.decide(ctx.inputText()).isYes("Is the customer angry?", 0.75),
 *             ctx -> escalationService.open(ctx))
 *     .run(ticketText);
 * }</pre>
 *
 * <p><b>Analogy:</b> a thermometer, not a doctor. It gives you 38.7 °C. What counts as a fever, and
 * what you do about it, stays with the person who has to answer for the decision.</p>
 *
 * @see EasyAI#decide(String)
 * @see Decisions
 * @see DecisionConfig
 */
public class DecisionBuilder {

    /** The question name used by the single-question shortcuts, which expose no names at all. */
    private static final String SINGLE = "answer";

    private final String input;
    private final Map<String, Question> questions = new LinkedHashMap<>();

    private DecisionConfig config;
    private String provider;
    private String modelName;
    private String apiKey;
    private String baseUrl;
    private EasyAIListener eventListener;

    /** Package-private: instances come from {@link EasyAI#decide(String)}. */
    DecisionBuilder(String input) {
        this.input = input;
    }

    // ------------------------------------------------------------------ configuration

    /**
     * Replace the whole configuration instead of overriding single values.
     *
     * @param config fully built decision settings; {@code null} restores properties-based defaults
     * @return this builder
     */
    public DecisionBuilder withConfig(DecisionConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Name the decision model for this call (default {@link DecisionConfig#DEFAULT_MODEL}).
     *
     * @param modelName the provider's decision-model id
     * @return this builder
     */
    public DecisionBuilder withModel(String modelName) {
        this.modelName = modelName;
        return this;
    }

    /**
     * Override the provider for this call.
     *
     * @param provider provider id (currently {@code "openai"})
     * @return this builder
     */
    public DecisionBuilder withProvider(String provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Override the API key for this call (otherwise {@code easyai.decision.api-key}, then
     * {@code easyai.api-key}).
     *
     * @param apiKey the provider API key
     * @return this builder
     */
    public DecisionBuilder withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Override the base URL for this call. Read {@link DecisionConfig} first: a decision model needs
     * log-probabilities, which not every OpenAI-compatible endpoint provides.
     *
     * @param baseUrl the endpoint to call
     * @return this builder
     */
    public DecisionBuilder withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Register a listener for the live {@link dyntabs.ai.event.EasyAIEvent} stream, so a decision
     * appears in your activity timeline like every other EasyAI operation, under
     * {@link Source#DECISION}.
     *
     * <p>The event detail carries the <b>numbers and the threshold</b>, not just the verdict — which
     * is what turns a timeline into an audit trail.</p>
     *
     * @param eventListener the listener (may be {@code null} to disable events)
     * @return this builder
     */
    public DecisionBuilder withEventListener(EasyAIListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    // ------------------------------------------------------------------ questions (multi)

    /**
     * Add a yes/no question.
     *
     * @param name     the name to read the answer back by
     * @param question the question, phrased so that "yes" is unambiguous
     * @return this builder
     */
    public DecisionBuilder yesNo(String name, String question) {
        return add(name, YesNoQuestion.of(requireText(question, "question")));
    }

    /**
     * Add a yes/no question, spelling out what counts as yes and what counts as no.
     *
     * <p>Worth the extra two arguments when the boundary is the hard part — "urgent" means different
     * things to a support desk and to a payments team, and saying so beats hoping the model guesses
     * your definition.</p>
     *
     * @param name     the name to read the answer back by
     * @param question the question
     * @param yesWhen  what makes the answer yes
     * @param noWhen   what makes the answer no
     * @return this builder
     */
    public DecisionBuilder yesNo(String name, String question, String yesWhen, String noWhen) {
        return add(name, YesNoQuestion.builder()
                .text(requireText(question, "question"))
                .yesWhen(yesWhen)
                .noWhen(noWhen)
                .build());
    }

    /**
     * Add a single-choice question.
     *
     * @param name     the name to read the answer back by
     * @param question the question
     * @param options  at least two options; the answer comes back as one of these strings
     * @return this builder
     */
    public DecisionBuilder choice(String name, String question, String... options) {
        return choice(name, question, Arrays.asList(requireOptions(options)));
    }

    /**
     * Add a single-choice question from a list.
     *
     * @param name     the name to read the answer back by
     * @param question the question
     * @param options  at least two options
     * @return this builder
     */
    public DecisionBuilder choice(String name, String question, List<String> options) {
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("A choice question needs at least two options.");
        }
        return add(name, ChoiceQuestion.of(requireText(question, "question"), options));
    }

    /**
     * Add a single-choice question whose options carry descriptions.
     *
     * <p>Use this when the option names are codes your system understands but a model would not —
     * {@code "L2_NET"} means nothing on its own, {@code "L2_NET" -> "network/connectivity faults"}
     * does.</p>
     *
     * @param name                the name to read the answer back by
     * @param question            the question
     * @param optionDescriptions  option → what it means; at least two entries
     * @return this builder
     */
    public DecisionBuilder choice(String name, String question, Map<String, String> optionDescriptions) {
        if (optionDescriptions == null || optionDescriptions.size() < 2) {
            throw new IllegalArgumentException("A choice question needs at least two options.");
        }
        return add(name, ChoiceQuestion.of(requireText(question, "question"), optionDescriptions));
    }

    /**
     * Add an ordered-scale question.
     *
     * <p>The answer is a probability-weighted position across the levels, so it can land
     * <em>between</em> two of them — which is usually the honest answer.</p>
     *
     * @param name     the name to read the answer back by
     * @param question the question
     * @param levels   at least two levels, in order from lowest to highest
     * @return this builder
     */
    public DecisionBuilder scale(String name, String question, String... levels) {
        if (levels == null || levels.length < 2) {
            throw new IllegalArgumentException("A scale question needs at least two levels, in order.");
        }
        return add(name, ScaleQuestion.builder()
                .text(requireText(question, "question"))
                .levels(Arrays.asList(levels))
                .build());
    }

    /**
     * Send every question added so far as <b>one</b> call.
     *
     * @return the answers
     * @throws IllegalArgumentException if no question was added
     */
    public Decisions ask() {
        return run(questions, null);
    }

    // ------------------------------------------------------------------ single-question shortcuts

    /**
     * Ask one yes/no question and decide at your threshold — the shortest useful form.
     *
     * @param question  the question
     * @param threshold the probability at or above which this counts as "yes" (0.0–1.0)
     * @return {@code true} if the reported probability reaches {@code threshold}
     */
    public boolean isYes(String question, double threshold) {
        // Checked BEFORE the call, not after: writing 80 instead of 0.80 is the classic slip, and
        // discovering it only once the provider has been paid for the call is no help at all.
        // (Decisions.isYes keeps the same guard for the named, multi-question path.)
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0 but was " + threshold
                    + ". It is a probability, not a percentage.");
        }
        Decisions decisions = single(YesNoQuestion.of(requireText(question, "question")), question, threshold);
        return decisions.isYes(SINGLE, threshold);
    }

    /**
     * Ask one yes/no question and get the raw probability, deciding yourself.
     *
     * @param question the question
     * @return the probability of "yes", 0.0–1.0
     */
    public double probability(String question) {
        return single(YesNoQuestion.of(requireText(question, "question")), question, null)
                .probability(SINGLE);
    }

    /**
     * Ask one choice question and get the selected option.
     *
     * @param question the question
     * @param options  at least two options
     * @return the chosen option, exactly as spelled here
     */
    public String choose(String question, String... options) {
        ChoiceQuestion q = ChoiceQuestion.of(requireText(question, "question"),
                Arrays.asList(requireOptions(options)));
        return single(q, question, null).choice(SINGLE);
    }

    /**
     * Ask one scale question and get the weighted position across the levels.
     *
     * @param question the question
     * @param levels   at least two levels, in order from lowest to highest
     * @return the weighted index, from 0.0 to {@code levels.length - 1}
     */
    public double rate(String question, String... levels) {
        if (levels == null || levels.length < 2) {
            throw new IllegalArgumentException("A scale question needs at least two levels, in order.");
        }
        ScaleQuestion q = ScaleQuestion.builder()
                .text(requireText(question, "question"))
                .levels(Arrays.asList(levels))
                .build();
        return single(q, question, null).mean(SINGLE);
    }

    // ------------------------------------------------------------------ internals

    /**
     * Run a one-question request. Refuses to mix with the named-question mode, because silently
     * dropping questions a caller already added would be worse than saying so.
     */
    private Decisions single(Question question, String questionText, Double threshold) {
        if (!questions.isEmpty()) {
            throw new IllegalStateException("Questions were already added with yesNo/choice/scale; "
                    + "finish that request with ask() instead of using a single-question shortcut.");
        }
        return run(Map.of(SINGLE, question), new Narration(questionText, threshold));
    }

    /** Resolve config, emit events around the call, and keep the stack trace honest on failure. */
    private Decisions run(Map<String, Question> toAsk, Narration narration) {
        DecisionConfig resolved = resolve();
        EventEmitter emitter = new EventEmitter(Source.DECISION, eventListener);
        emitter.started(narration == null ? "Deciding" : "Deciding: " + narration.question());
        try {
            Decisions decisions = DecisionEngine.decide(input, toAsk, resolved);
            emitter.result("Decided", detail(decisions, narration));
            return decisions;
        } catch (RuntimeException e) {
            emitter.error("Decision failed", EasyAI.extractErrorMessage(e));
            throw e;
        }
    }

    /**
     * Build the event detail. For a thresholded yes/no it spells out the whole judgement —
     * measurement, threshold and verdict — which is exactly what an audit trail needs.
     */
    private String detail(Decisions decisions, Narration narration) {
        if (narration != null && narration.threshold() != null) {
            double p = decisions.probability(SINGLE);
            double t = narration.threshold();
            // Locale.ROOT: an audit line must read the same on every server.
            return String.format(Locale.ROOT, "%s -> %.2f (threshold %.2f) -> %s",
                    narration.question(), p, t, p >= t ? "yes" : "no");
        }
        return decisions.summary();
    }

    /** Start from properties (or the supplied config) and lay the per-call overrides on top. */
    private DecisionConfig resolve() {
        DecisionConfig base = config != null ? config : DecisionConfig.forDeciding();
        DecisionConfig.Builder builder = base.toBuilder();
        if (provider != null) {
            builder.provider(provider);
        }
        if (modelName != null) {
            builder.modelName(modelName);
        }
        if (apiKey != null) {
            builder.apiKey(apiKey);
        }
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private DecisionBuilder add(String name, Question question) {
        String key = requireText(name, "question name");
        if (questions.containsKey(key)) {
            throw new IllegalArgumentException("A question named '" + key + "' was already added; "
                    + "names must be unique because the answers are read back by them.");
        }
        questions.put(key, question);
        return this;
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + what + " must not be blank.");
        }
        return value.trim();
    }

    private static String[] requireOptions(String[] options) {
        if (options == null || options.length < 2) {
            throw new IllegalArgumentException("A choice question needs at least two options.");
        }
        return options;
    }

    /** What the single-question shortcuts know that the generic path does not, for the event text. */
    private record Narration(String question, Double threshold) {
    }
}
