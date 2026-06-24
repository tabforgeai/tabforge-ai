package dyntabs.ai.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * Verifies the immutable {@link UserActivityEvent}: the builder maps every field, an unset timestamp
 * defaults to "now", the entities list is unmodifiable, and {@link UserActivityEvent#primaryEntity()}
 * returns the first touched entity (or {@code null}).
 */
class UserActivityEventTest {

    @Test
    void builderMapsAllFields() {
        Instant ts = Instant.parse("2026-06-23T10:15:00Z");
        EntityRef order = EntityRef.of("order", "4711", "label", "Order #4711");

        UserActivityEvent e = UserActivityEvent.builder(Type.BUSINESS_ACTION)
                .timestamp(ts)
                .sessionId("S1")
                .tabId("r3")
                .verb("approve")
                .entity(order)
                .text("looks good")
                .build();

        assertThat(e.type()).isEqualTo(Type.BUSINESS_ACTION);
        assertThat(e.timestamp()).isEqualTo(ts);
        assertThat(e.sessionId()).isEqualTo("S1");
        assertThat(e.tabId()).isEqualTo("r3");
        assertThat(e.verb()).isEqualTo("approve");
        assertThat(e.entities()).containsExactly(order);
        assertThat(e.text()).isEqualTo("looks good");
        assertThat(e.primaryEntity()).isEqualTo(order);
    }

    @Test
    void timestampDefaultsToNowAndOptionalFieldsAreNull() {
        UserActivityEvent e = UserActivityEvent.builder(Type.NAVIGATION).build();
        assertThat(e.timestamp()).isNotNull();
        assertThat(e.sessionId()).isNull();
        assertThat(e.tabId()).isNull();
        assertThat(e.verb()).isNull();
        assertThat(e.text()).isNull();
        assertThat(e.entities()).isEmpty();
        assertThat(e.primaryEntity()).isNull();
    }

    @Test
    void entitiesListIsUnmodifiable() {
        UserActivityEvent e = UserActivityEvent.builder(Type.SEARCH)
                .entity(EntityRef.of("order", "1"))
                .build();
        assertThatThrownBy(() -> e.entities().add(EntityRef.of("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entitiesVarargsAndPrimaryEntityPickFirst() {
        EntityRef a = EntityRef.of("order", "1");
        EntityRef b = EntityRef.of("order", "2");
        UserActivityEvent e = UserActivityEvent.builder(Type.CUSTOM)
                .entities(List.of(a, b))
                .build();
        assertThat(e.entities()).containsExactly(a, b);
        assertThat(e.primaryEntity()).isEqualTo(a);
    }

    @Test
    void nullTypeIsRejected() {
        assertThatThrownBy(() -> UserActivityEvent.builder(null).build())
                .isInstanceOf(NullPointerException.class);
    }
}
