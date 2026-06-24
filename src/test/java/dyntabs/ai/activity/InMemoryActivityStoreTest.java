package dyntabs.ai.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * Verifies the default {@link InMemoryActivityStore}: per-session ring-buffer eviction of the oldest
 * event, chronological (oldest-first) query results, the "most recent N" limit, and that a scoped
 * query only returns its own session's events.
 */
class InMemoryActivityStoreTest {

    private static final Instant T0 = Instant.parse("2026-06-23T10:00:00Z");

    private static UserActivityEvent event(String session, int seq) {
        return UserActivityEvent.builder(Type.BUSINESS_ACTION)
                .sessionId(session)
                .verb("v" + seq)
                .timestamp(T0.plusSeconds(seq))
                .build();
    }

    @Test
    void recordsAndReturnsChronologically() {
        InMemoryActivityStore store = new InMemoryActivityStore();
        store.record(event("S1", 1));
        store.record(event("S1", 2));
        store.record(event("S1", 3));

        List<UserActivityEvent> all = store.recent("S1", null, 0);
        assertThat(all).extracting(UserActivityEvent::verb).containsExactly("v1", "v2", "v3");
    }

    @Test
    void evictsOldestWhenCapacityExceeded() {
        InMemoryActivityStore store = new InMemoryActivityStore(3);
        for (int i = 1; i <= 5; i++) {
            store.record(event("S1", i));
        }
        // Capacity 3 → the two oldest (v1, v2) are evicted.
        assertThat(store.recent("S1", null, 0))
                .extracting(UserActivityEvent::verb)
                .containsExactly("v3", "v4", "v5");
    }

    @Test
    void limitKeepsMostRecentN() {
        InMemoryActivityStore store = new InMemoryActivityStore();
        for (int i = 1; i <= 5; i++) {
            store.record(event("S1", i));
        }
        assertThat(store.recent("S1", null, 2))
                .extracting(UserActivityEvent::verb)
                .containsExactly("v4", "v5");
    }

    @Test
    void scopedQueryIsolatesSessions() {
        InMemoryActivityStore store = new InMemoryActivityStore();
        store.record(event("S1", 1));
        store.record(event("S2", 2));
        store.record(event("S1", 3));

        assertThat(store.recent("S1", null, 0))
                .extracting(UserActivityEvent::verb).containsExactly("v1", "v3");
        assertThat(store.recent("S2", null, 0))
                .extracting(UserActivityEvent::verb).containsExactly("v2");
    }

    @Test
    void unscopedQueryReturnsAllSessionsChronologically() {
        InMemoryActivityStore store = new InMemoryActivityStore();
        store.record(event("S1", 1));
        store.record(event("S2", 2));
        store.record(event("S1", 3));

        // No sessionId on the query → scan all buffers, merged in timestamp order.
        List<UserActivityEvent> all = store.query(ActivityQuery.builder().build());
        assertThat(all).extracting(UserActivityEvent::verb).containsExactly("v1", "v2", "v3");
    }

    @Test
    void emptyStoreReturnsEmptyList() {
        assertThat(new InMemoryActivityStore().recent("nobody", null, 0)).isEmpty();
    }
}
