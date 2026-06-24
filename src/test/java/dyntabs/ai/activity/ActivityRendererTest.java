package dyntabs.ai.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * Verifies {@link ActivityRenderer#compactDefault()}: an empty timeline renders to an empty string,
 * a non-empty one renders a header plus one labelled bullet per event, and a missing verb falls back
 * to a humanised category word.
 */
class ActivityRendererTest {

    private final ActivityRenderer renderer = ActivityRenderer.compactDefault();

    @Test
    void emptyTimelineRendersEmptyString() {
        assertThat(renderer.render(List.of())).isEmpty();
    }

    @Test
    void rendersHeaderAndOneBulletPerEvent() {
        UserActivityEvent open = UserActivityEvent.builder(Type.NAVIGATION)
                .verb("open")
                .entity(EntityRef.of("order", "4711", "label", "Order #4711 — ACME"))
                .timestamp(Instant.parse("2026-06-23T10:00:00Z"))
                .build();
        UserActivityEvent search = UserActivityEvent.builder(Type.SEARCH)
                .verb("search")
                .text("unpaid invoices")
                .timestamp(Instant.parse("2026-06-23T10:01:00Z"))
                .build();

        String out = renderer.render(List.of(open, search));

        assertThat(out).startsWith("Recent user activity in this tab (oldest first):");
        assertThat(out).contains("open Order #4711 — ACME");
        assertThat(out).contains("search \"unpaid invoices\"");
        // One header line + two event lines, no trailing blank line.
        assertThat(out.split("\n")).hasSize(3);
    }

    @Test
    void missingVerbFallsBackToHumanisedType() {
        UserActivityEvent e = UserActivityEvent.builder(Type.NOTE)
                .text("remember this")
                .timestamp(Instant.parse("2026-06-23T10:00:00Z"))
                .build();
        assertThat(renderer.render(List.of(e))).contains("noted \"remember this\"");
    }
}
