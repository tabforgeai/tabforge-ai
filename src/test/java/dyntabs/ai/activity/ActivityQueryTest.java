package dyntabs.ai.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * Verifies {@link ActivityQuery#matches(UserActivityEvent)} field-filter semantics: a {@code null}
 * filter field matches anything, a set field must match exactly, {@code since} is an inclusive lower
 * bound, and an empty type set means "all types". The limit is intentionally not applied by matches().
 */
class ActivityQueryTest {

    private static UserActivityEvent event(String session, String tab, Type type, Instant ts) {
        return UserActivityEvent.builder(type)
                .sessionId(session).tabId(tab).timestamp(ts).build();
    }

    @Test
    void nullFieldsMatchAnything() {
        ActivityQuery q = ActivityQuery.builder().build();
        assertThat(q.matches(event("S1", "r1", Type.NAVIGATION, Instant.now()))).isTrue();
    }

    @Test
    void sessionAndTabMustMatchExactlyWhenSet() {
        ActivityQuery q = ActivityQuery.builder().sessionId("S1").tabId("r1").build();
        assertThat(q.matches(event("S1", "r1", Type.NOTE, Instant.now()))).isTrue();
        assertThat(q.matches(event("S2", "r1", Type.NOTE, Instant.now()))).isFalse();
        assertThat(q.matches(event("S1", "r2", Type.NOTE, Instant.now()))).isFalse();
    }

    @Test
    void sinceIsInclusiveLowerBound() {
        Instant cut = Instant.parse("2026-06-23T10:00:00Z");
        ActivityQuery q = ActivityQuery.builder().since(cut).build();

        assertThat(q.matches(event("S", "r", Type.NOTE, cut))).isTrue();                       // equal → included
        assertThat(q.matches(event("S", "r", Type.NOTE, cut.plusSeconds(1)))).isTrue();
        assertThat(q.matches(event("S", "r", Type.NOTE, cut.minusSeconds(1)))).isFalse();
    }

    @Test
    void emptyTypeSetMeansAllTypes_butSetRestricts() {
        assertThat(ActivityQuery.builder().build()
                .matches(event("S", "r", Type.SEARCH, Instant.now()))).isTrue();

        ActivityQuery onlyNav = ActivityQuery.builder().type(Type.NAVIGATION).build();
        assertThat(onlyNav.matches(event("S", "r", Type.NAVIGATION, Instant.now()))).isTrue();
        assertThat(onlyNav.matches(event("S", "r", Type.SEARCH, Instant.now()))).isFalse();
    }

    @Test
    void typesAreCarriedThroughTheBuilder() {
        ActivityQuery q = ActivityQuery.builder().type(Type.NOTE).type(Type.SEARCH).limit(5).build();
        assertThat(q.types()).containsExactlyInAnyOrder(Type.NOTE, Type.SEARCH);
        assertThat(q.limit()).isEqualTo(5);
    }
}
