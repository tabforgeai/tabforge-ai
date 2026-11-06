package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dyntabs.ai.decision.DecisionConfig;
import dyntabs.ai.event.EasyAIEvent;
import dyntabs.ai.event.EasyAIEvent.Phase;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;

/**
 * Tests the {@code EasyAI.decide(...)} facade: input validation, that the chain reaches the adapter
 * with per-call overrides applied, and that a decision narrates itself on the event stream.
 *
 * <p>Configs are supplied explicitly (and deliberately without an API key) so no test ever reaches a
 * provider, and the results do not depend on what happens to be on the classpath.</p>
 */
class DecisionBuilderTest {

    private static final String TICKET = "Nothing works, I cannot log in and the deadline is today.";

    /** Deliberately unusable: no API key, so the call stops at our own guard. */
    private static DecisionConfig withoutKey() {
        return DecisionConfig.builder().build();
    }

    private static DecisionBuilder decide() {
        return EasyAI.decide(TICKET).withConfig(withoutKey());
    }

    @Test
    @DisplayName("EasyAI.decide() hands back a fresh builder")
    void facadeReturnsBuilder() {
        assertThat(EasyAI.decide(TICKET)).isNotNull().isNotSameAs(EasyAI.decide(TICKET));
    }

    // ---------------------------------------------------------------- reaching the adapter

    @Test
    @DisplayName("the single-question shortcut reaches the adapter — a missing key surfaces its guidance")
    void singleQuestionReachesAdapter() {
        assertThatThrownBy(() -> decide().isYes("Is the customer blocked?", 0.8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("easyai.decision.api-key")
                .hasMessageContaining("easyai.api-key");
    }

    @Test
    @DisplayName("the multi-question path reaches the adapter too")
    void multiQuestionReachesAdapter() {
        assertThatThrownBy(() -> decide()
                .yesNo("urgent", "Is the customer blocked?")
                .choice("team", "Which team?", "billing", "technical")
                .ask())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("easyai.decision.api-key");
    }

    @Test
    @DisplayName("per-call overrides are applied on top of the base config")
    void overridesAreApplied() {
        // The key override satisfies our key guard, so the next gate - the unknown provider - is
        // what fails. That proves both overrides travelled all the way down to the adapter.
        assertThatThrownBy(() -> EasyAI.decide(TICKET)
                .withConfig(DecisionConfig.builder().provider("nope").build())
                .withApiKey("supplied-at-call-time")
                .withModel("some-decider")
                .withBaseUrl("https://api.openai.com/v1/")
                .isYes("Is the customer blocked?", 0.8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    // ---------------------------------------------------------------- validation

    @Test
    @DisplayName("asking with no questions says exactly what to call instead")
    void noQuestionsIsExplained() {
        assertThatThrownBy(() -> decide().ask())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one question");
    }

    @Test
    @DisplayName("blank input is rejected before any provider call")
    void blankInputIsRejected() {
        assertThatThrownBy(() -> EasyAI.decide("   ").withConfig(withoutKey())
                .isYes("Is it urgent?", 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input is blank");
    }

    @Test
    @DisplayName("a blank question is rejected")
    void blankQuestionIsRejected() {
        assertThatThrownBy(() -> decide().isYes("  ", 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question");
    }

    @Test
    @DisplayName("a choice needs at least two options, and a scale at least two levels")
    void degenerateQuestionsAreRejected() {
        assertThatThrownBy(() -> decide().choose("Which team?", "billing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two options");

        assertThatThrownBy(() -> decide().choice("team", "Which team?", List.of("only-one")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two options");

        assertThatThrownBy(() -> decide().choice("team", "Which team?", Map.of("a", "only one")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two options");

        assertThatThrownBy(() -> decide().rate("How bad?", "low"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two levels");
    }

    @Test
    @DisplayName("duplicate question names are refused, since answers are read back by name")
    void duplicateNamesAreRefused() {
        assertThatThrownBy(() -> decide()
                .yesNo("urgent", "Is it urgent?")
                .yesNo("urgent", "Is it really urgent?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already added");
    }

    @Test
    @DisplayName("mixing a shortcut into a named request fails loudly rather than dropping questions")
    void shortcutAfterNamedQuestionsIsRefused() {
        assertThatThrownBy(() -> decide()
                .yesNo("urgent", "Is it urgent?")
                .isYes("Is it really urgent?", 0.8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ask()");
    }

    @Test
    @DisplayName("an out-of-range threshold is caught before the call, not after")
    void thresholdIsValidatedEarly() {
        // 80 instead of 0.80 is the classic slip; it must not reach a provider at all.
        assertThatThrownBy(() -> decide().isYes("Is it urgent?", 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a percentage");
    }

    // ---------------------------------------------------------------- events

    @Test
    @DisplayName("a failed decision narrates STARTED then ERROR under Source.DECISION")
    void failureIsNarrated() {
        List<EasyAIEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> decide()
                .withEventListener(events::add)
                .isYes("Is the customer blocked?", 0.8))
                .isInstanceOf(IllegalStateException.class);

        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(e -> assertThat(e.source()).isEqualTo(Source.DECISION));
        assertThat(events.get(0).phase()).isEqualTo(Phase.STARTED);
        assertThat(events.get(0).title()).contains("Is the customer blocked?");   // the question itself
        assertThat(events.get(1).phase()).isEqualTo(Phase.ERROR);
        assertThat(events.get(1).status()).isEqualTo(Status.ERROR);
        assertThat(events.get(1).detail()).contains("easyai.decision.api-key");
    }

    @Test
    @DisplayName("no listener means no events and no crash")
    void noListenerIsSilent() {
        assertThatThrownBy(() -> decide().probability("Is it urgent?"))
                .isInstanceOf(IllegalStateException.class);   // and nothing else blew up
    }

    @Test
    @DisplayName("the threshold is required: there is no isYes(question) overload")
    void thereIsNoThresholdlessIsYes() {
        // A compile-time guarantee, asserted here as documentation of the design decision: a
        // probability only becomes a decision at a line somebody chose.
        boolean hasThresholdlessIsYes = java.util.Arrays.stream(DecisionBuilder.class.getMethods())
                .anyMatch(m -> m.getName().equals("isYes") && m.getParameterCount() == 1);

        assertThat(hasThresholdlessIsYes).isFalse();
    }
}
