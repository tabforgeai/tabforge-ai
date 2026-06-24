package dyntabs.ai.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * Verifies {@link ActivityContext}: it reads the configured slice from the store and renders it,
 * honours the session/tab scope, applies the limit, drops events older than the window, and renders
 * an empty string when nothing matches.
 */
class ActivityContextTest {

    private InMemoryActivityStore storeWith(UserActivityEvent... events) {
        InMemoryActivityStore store = new InMemoryActivityStore();
        for (UserActivityEvent e : events) {
            store.record(e);
        }
        return store;
    }

    private UserActivityEvent action(String session, String tab, String verb, Instant ts) {
        return UserActivityEvent.builder(Type.BUSINESS_ACTION)
                .sessionId(session).tabId(tab).verb(verb).timestamp(ts).build();
    }

    @Test
    void rendersRecentSliceForSessionAndTab() {
        Instant now = Instant.now();
        InMemoryActivityStore store = storeWith(
                action("S1", "r1", "open", now.minusSeconds(30)),
                action("S1", "r1", "approve", now.minusSeconds(10)),
                action("S2", "r9", "noise", now.minusSeconds(5)));   // other session, must be excluded

        String out = ActivityContext.of(store).forSession("S1").forTab("r1").build().render();

        assertThat(out).contains("open").contains("approve");
        assertThat(out).doesNotContain("noise");
    }

    @Test
    void limitKeepsMostRecentEvents() {
        Instant now = Instant.now();
        InMemoryActivityStore store = storeWith(
                action("S1", "r1", "alpha", now.minusSeconds(30)),
                action("S1", "r1", "beta", now.minusSeconds(20)),
                action("S1", "r1", "gamma", now.minusSeconds(10)));

        String out = ActivityContext.of(store).forSession("S1").forTab("r1").limit(1).build().render();

        assertThat(out).contains("gamma");
        assertThat(out).doesNotContain("alpha").doesNotContain("beta");
    }

    @Test
    void windowExcludesOlderEvents() {
        Instant now = Instant.now();
        InMemoryActivityStore store = storeWith(
                action("S1", "r1", "stale", now.minusSeconds(3600)),
                action("S1", "r1", "fresh", now.minusSeconds(10)));

        String out = ActivityContext.of(store)
                .forSession("S1").forTab("r1").window(Duration.ofMinutes(1)).build().render();

        assertThat(out).contains("fresh");
        assertThat(out).doesNotContain("stale");
    }

    @Test
    void noMatchingActivityRendersEmptyString() {
        InMemoryActivityStore store = storeWith(action("S1", "r1", "x", Instant.now()));
        String out = ActivityContext.of(store).forSession("OTHER").build().render();
        assertThat(out).isEmpty();
    }
}
