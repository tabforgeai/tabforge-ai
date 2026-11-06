package dyntabs.ai.decision;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.openai.OpenAiDecisionModel;

/**
 * Bridges EasyAI to LangChain4J's {@link DecisionModel} — the counterpart of
 * {@link dyntabs.ai.voice.VoiceEngine} for decisions rather than audio.
 *
 * <h2>What a decision model is, and why it is not a chat call</h2>
 * <p>Classifying, routing or gating used to mean a full chat completion with structured output: slow,
 * chatty, and it hands you a <em>word</em> ({@code "urgent"}) when what you needed was a
 * <em>number</em> you can threshold. A decision model answers a fixed set of questions in one small
 * call and returns <b>probabilities</b>. That changes the engineering: you keep the decision in Java,
 * at a threshold you wrote down, with the measurement in your audit log.</p>
 *
 * <p>It also costs a fraction of a chat turn, and several questions ride in <b>one</b> call — which
 * is why the API lets you ask them together instead of once per question.</p>
 *
 * <h2>Providers</h2>
 * <p>Only {@code "openai"} is built in. Note that this is <em>not</em> a capability every
 * OpenAI-compatible service implements: probabilities need token log-probabilities, which Groq, for
 * one, rejects. See {@link DecisionConfig} for what to configure when your chat provider is not
 * OpenAI. Other providers (Ollama, TypeSafe) plug in here and nowhere else.</p>
 *
 * <p><b>Analogy:</b> a breathalyser rather than a conversation about whether someone seems drunk.
 * It returns a reading; the legal limit is set elsewhere, by someone accountable for it.</p>
 *
 * @see DecisionConfig
 * @see Decisions
 * @see dyntabs.ai.EasyAI#decide(String)
 */
public final class DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    private DecisionEngine() {
    }

    /**
     * Ask one or more questions about the same input, in a single call.
     *
     * @param input     the text being judged (a ticket, an email, a transcript, a diff)
     * @param questions the questions, keyed by the name you will read the answers back by
     * @param config    which decision model to call and how to reach it
     * @return the answers, wrapped so that absent probabilities cannot surprise you
     * @throws IllegalArgumentException if {@code input} is blank or {@code questions} is empty
     * @throws IllegalStateException    if no API key is configured
     */
    public static Decisions decide(String input, Map<String, Question> questions, DecisionConfig config) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Nothing to decide about: the input is blank.");
        }
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Nothing to decide: add at least one question "
                    + "(isYes/choose/rate, or yesNo/choice/scale followed by ask()).");
        }
        requireKey(config);

        DecisionRequest request = DecisionRequest.builder()
                .input(input)
                .questions(questions)
                .build();

        log.debug("Deciding {} question(s) with {}", questions.size(), config);
        return new Decisions(model(config).decide(request));
    }

    /**
     * Build the LangChain4J decision model described by {@code config}.
     *
     * <p>Exposed as the escape hatch to the raw LangChain4J model, so choosing EasyAI never means
     * losing access to the layer underneath.</p>
     *
     * @param config the decision settings
     * @return a ready {@link DecisionModel}
     */
    public static DecisionModel model(DecisionConfig config) {
        String provider = config.provider() == null ? "openai" : config.provider().toLowerCase();
        if (!"openai".equals(provider)) {
            throw new IllegalArgumentException("Unsupported decision provider: '" + provider
                    + "'. Built in: 'openai'. Note that decision models need token log-probabilities, "
                    + "which not every OpenAI-compatible service supports.");
        }
        OpenAiDecisionModel.OpenAiDecisionModelBuilder builder = OpenAiDecisionModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.modelName());
        if (config.baseUrl() != null) {
            builder.baseUrl(config.baseUrl());
        }
        return builder.build();
    }

    /** Fail early and say which property to set, rather than letting the provider throw. */
    private static void requireKey(DecisionConfig config) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("No API key configured for decisions. Set "
                    + "easyai.decision.api-key (or easyai.api-key to share the chat key) in "
                    + "easyai.properties, or pass it programmatically.");
        }
    }
}
