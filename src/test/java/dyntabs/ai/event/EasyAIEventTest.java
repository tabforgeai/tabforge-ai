package dyntabs.ai.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dyntabs.ai.event.EasyAIEvent.Phase;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;

/**
 * Verifies the immutable {@link EasyAIEvent} value: its builder maps every field through, an
 * unset timestamp defaults to "now", and {@code toString} surfaces the key identifying fields.
 */
class EasyAIEventTest {

    @Test
    void builderMapsAllFields() {
        Instant ts = Instant.parse("2026-06-05T10:15:00Z");
        EasyAIEvent e = EasyAIEvent.builder(Source.AGENT, Phase.STEP)
                .status(Status.SUCCESS)
                .title("checkStock")
                .detail("42 in stock")
                .toolName("checkStock")
                .sequence(7L)
                .timestamp(ts)
                .build();

        assertThat(e.source()).isEqualTo(Source.AGENT);
        assertThat(e.phase()).isEqualTo(Phase.STEP);
        assertThat(e.status()).isEqualTo(Status.SUCCESS);
        assertThat(e.title()).isEqualTo("checkStock");
        assertThat(e.detail()).isEqualTo("42 in stock");
        assertThat(e.toolName()).isEqualTo("checkStock");
        assertThat(e.sequence()).isEqualTo(7L);
        assertThat(e.timestamp()).isEqualTo(ts);
    }

    @Test
    void timestampDefaultsToNowWhenUnset() {
        EasyAIEvent e = EasyAIEvent.builder(Source.CHAT, Phase.STARTED).build();
        assertThat(e.timestamp()).isNotNull();
    }

    @Test
    void optionalFieldsAreNullWhenUnset() {
        EasyAIEvent e = EasyAIEvent.builder(Source.CHAT, Phase.STARTED).build();
        assertThat(e.status()).isNull();
        assertThat(e.title()).isNull();
        assertThat(e.detail()).isNull();
        assertThat(e.toolName()).isNull();
        assertThat(e.sequence()).isZero();
    }

    @Test
    void toStringContainsSourcePhaseAndSequence() {
        String s = EasyAIEvent.builder(Source.EXTRACT, Phase.RESULT)
                .status(Status.SUCCESS).title("Extracted Invoice").sequence(3L).build()
                .toString();

        assertThat(s).contains("EXTRACT").contains("RESULT").contains("#3").contains("Extracted Invoice");
    }
}
