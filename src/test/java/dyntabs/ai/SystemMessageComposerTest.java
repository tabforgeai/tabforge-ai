package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dyntabs.ai.activity.ActivityContext;
import dyntabs.ai.activity.InMemoryActivityStore;
import dyntabs.ai.activity.UserActivityEvent;
import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * Verifies the package-private {@link SystemMessageComposer#compose(String, ActivityContext)} fusion
 * rule used by {@link Conversation} and {@link AssistantBuilder}: base + activity are joined, either
 * one alone passes through, and "neither" yields {@code null} (meaning "no system message").
 */
class SystemMessageComposerTest {

    /** An ActivityContext whose render() produces a known, non-empty briefing. */
    private ActivityContext contextWithOneEvent(String session) {
        InMemoryActivityStore store = new InMemoryActivityStore();
        store.record(UserActivityEvent.builder(Type.NAVIGATION)
                .sessionId(session).verb("open")
                .timestamp(Instant.now())
                .build());
        return ActivityContext.of(store).forSession(session).build();
    }

    @Test
    void baseAndActivityAreJoined() {
        String out = SystemMessageComposer.compose("You are a bot.", contextWithOneEvent("S1"));
        assertThat(out).startsWith("You are a bot.\n\n");
        assertThat(out).contains("Recent user activity");
    }

    @Test
    void onlyBasePassesThrough() {
        assertThat(SystemMessageComposer.compose("You are a bot.", null)).isEqualTo("You are a bot.");
    }

    @Test
    void onlyActivityIsReturnedAlone() {
        String out = SystemMessageComposer.compose("  ", contextWithOneEvent("S1"));
        assertThat(out).startsWith("Recent user activity");
    }

    @Test
    void neitherYieldsNull() {
        assertThat(SystemMessageComposer.compose(null, null)).isNull();
        assertThat(SystemMessageComposer.compose("  ", null)).isNull();
    }

    @Test
    void emptyActivityWithBaseReturnsJustBase() {
        // A context that matches nothing renders "" → should not add a trailing separator.
        InMemoryActivityStore empty = new InMemoryActivityStore();
        ActivityContext noActivity = ActivityContext.of(empty).forSession("NONE").build();
        assertThat(SystemMessageComposer.compose("Base only.", noActivity)).isEqualTo("Base only.");
    }
}
