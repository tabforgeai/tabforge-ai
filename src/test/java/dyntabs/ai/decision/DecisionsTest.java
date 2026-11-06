package dyntabs.ai.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;

/**
 * Tests the decision-result wrapper against <b>real</b> LangChain4J answer objects, not mocks — the
 * whole point of the wrapper is how the real ones behave when probabilities are missing.
 */
class DecisionsTest {

    private static Decisions of(String name, dev.langchain4j.model.decision.response.DecisionAnswer answer) {
        return new Decisions(DecisionResponse.builder()
                .answer(name, answer)
                .modelName("gpt-6-luna")
                .build());
    }

    // ---------------------------------------------------------------- yes / no

    @Test
    @DisplayName("a yes/no answer exposes its probability and decides at the given threshold")
    void yesNoThresholding() {
        Decisions d = of("urgent", YesNoAnswer.of(0.91));

        assertThat(d.probability("urgent")).isEqualTo(0.91);
        assertThat(d.isYes("urgent", 0.80)).isTrue();
        assertThat(d.isYes("urgent", 0.95)).isFalse();
    }

    @Test
    @DisplayName("a threshold outside 0.0-1.0 is rejected — it is a probability, not a percentage")
    void thresholdMustBeAProbability() {
        Decisions d = of("urgent", YesNoAnswer.of(0.91));

        assertThatThrownBy(() -> d.isYes("urgent", 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a percentage");

        assertThatThrownBy(() -> d.isYes("urgent", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- choice

    @Test
    @DisplayName("a choice answer exposes the value, its probabilities and the runner-up margin")
    void choiceWithProbabilities() {
        Decisions d = of("team", ChoiceAnswer.builder()
                .value("technical")
                .options(List.of("billing", "technical", "sales"))
                .probabilities(Map.of("billing", 0.10, "technical", 0.70, "sales", 0.20))
                .confidence(0.88)
                .build());

        assertThat(d.choice("team")).isEqualTo("technical");
        assertThat(d.probabilityOf("team", "technical")).hasValue(0.70);
        assertThat(d.probabilities("team")).containsEntry("sales", 0.20);
        assertThat(d.margin("team")).isPresent();
        assertThat(d.confidence("team")).hasValue(0.88);
    }

    @Test
    @DisplayName("THE FOOTGUN: with no probabilities reported, accessors return empty instead of throwing")
    void missingProbabilitiesAreEmptyNotExplosive() {
        ChoiceAnswer bare = ChoiceAnswer.builder()
                .value("technical")
                .options(List.of("billing", "technical", "sales"))
                .build();

        // Raw LangChain4J throws IllegalStateException here; that is what we are shielding callers from.
        assertThatThrownBy(bare::margin).isInstanceOf(IllegalStateException.class);

        Decisions d = of("team", bare);

        assertThat(d.choice("team")).isEqualTo("technical");       // still usable
        assertThat(d.margin("team")).isEmpty();                    // and no explosion
        assertThat(d.probabilityOf("team", "technical")).isEmpty();
        assertThat(d.probabilities("team")).isEmpty();
    }

    @Test
    @DisplayName("an option that was never offered simply has no probability")
    void unknownOptionHasNoProbability() {
        Decisions d = of("team", ChoiceAnswer.builder()
                .value("technical")
                .options(List.of("billing", "technical"))
                .probabilities(Map.of("billing", 0.3, "technical", 0.7))
                .build());

        assertThat(d.probabilityOf("team", "legal")).isEmpty();
    }

    // ---------------------------------------------------------------- scale

    @Test
    @DisplayName("a scale answer keeps its in-between position rather than rounding it away")
    void scaleKeepsTheInBetweenValue() {
        Decisions d = of("severity", ScaleAnswer.builder()
                .mean(1.4)
                .probabilities(List.of(0.1, 0.4, 0.5))
                .confidence(0.6)
                .build());

        assertThat(d.mean("severity")).isEqualTo(1.4);
        assertThat(d.confidence("severity")).hasValue(0.6);
    }

    @Test
    @DisplayName("a missing confidence is empty, not null")
    void absentConfidenceIsEmpty() {
        Decisions d = of("severity", ScaleAnswer.builder().mean(1.0).build());

        assertThat(d.confidence("severity")).isEmpty();
    }

    // ---------------------------------------------------------------- mistakes

    @Test
    @DisplayName("a typo in the question name lists the names that DO exist")
    void unknownNameListsWhatExists() {
        Decisions d = of("urgent", YesNoAnswer.of(0.9));

        assertThatThrownBy(() -> d.probability("urgnet"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("urgnet")
                .hasMessageContaining("urgent");
    }

    @Test
    @DisplayName("reading an answer with the wrong accessor says which question and what it was")
    void wrongAccessorIsExplained() {
        Decisions d = of("team", ChoiceAnswer.builder()
                .value("sales").options(List.of("sales", "billing")).build());

        assertThatThrownBy(() -> d.isYes("team", 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team")
                .hasMessageContaining("ChoiceAnswer");

        assertThatThrownBy(() -> d.mean("team"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
    }

    @Test
    @DisplayName("a refusal is reportable, and reading it tells you to check isRefused first")
    void refusalIsExplicit() {
        Decisions d = of("sensitive", RefusalAnswer.of());

        assertThat(d.isRefused("sensitive")).isTrue();
        assertThatThrownBy(() -> d.probability("sensitive"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("isRefused");
    }

    // ---------------------------------------------------------------- reporting

    /**
     * Also a locale regression test: the dots below are deliberate. {@code String.format} without a
     * locale renders 0.91 as "0,91" on a comma-decimal machine (this project's own dev box, for
     * one), which would make the audit trail read differently per server. {@code Decisions} formats
     * with {@code Locale.ROOT} — do not "fix" these assertions to accept a comma.
     */
    @Test
    @DisplayName("summary carries the NUMBERS, so a timeline becomes an audit trail")
    void summaryCarriesNumbers() {
        Decisions d = new Decisions(DecisionResponse.builder()
                .answer("urgent", YesNoAnswer.of(0.91))
                .answer("team", ChoiceAnswer.builder()
                        .value("technical")
                        .options(List.of("billing", "technical"))
                        .probabilities(Map.of("billing", 0.29, "technical", 0.71))
                        .build())
                .answer("severity", ScaleAnswer.builder().mean(1.4).build())
                .modelName("gpt-6-luna")
                .build());

        String summary = d.summary();

        assertThat(summary).contains("urgent=0.91");
        assertThat(summary).contains("team=technical");
        assertThat(summary).contains("severity=1.40");
        assertThat(d.names()).containsExactlyInAnyOrder("urgent", "team", "severity");
        assertThat(d.modelName()).isEqualTo("gpt-6-luna");
        assertThat(d.raw()).isNotNull();
    }

    @Test
    @DisplayName("a refused answer shows as REFUSED in the summary rather than crashing it")
    void summaryToleratesRefusal() {
        assertThat(of("x", RefusalAnswer.of()).summary()).contains("REFUSED");
    }
}
