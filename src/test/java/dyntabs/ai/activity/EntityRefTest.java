package dyntabs.ai.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link EntityRef}: its factory methods, the "label or {@code type#id} fallback" rule,
 * attribute immutability, and the deliberate identity semantics (equality by type+id only).
 */
class EntityRefTest {

    @Test
    void bareFactoryHasNoAttributes() {
        EntityRef ref = EntityRef.of("order", "4711");
        assertThat(ref.type()).isEqualTo("order");
        assertThat(ref.id()).isEqualTo("4711");
        assertThat(ref.attributes()).isEmpty();
    }

    @Test
    void labelFallsBackToTypeHashIdWhenNoLabelAttribute() {
        assertThat(EntityRef.of("order", "4711").label()).isEqualTo("order#4711");
    }

    @Test
    void labelUsesLabelAttributeWhenPresent() {
        EntityRef ref = EntityRef.of("order", "4711", "label", "Order #4711 — ACME");
        assertThat(ref.label()).isEqualTo("Order #4711 — ACME");
        assertThat(ref.attribute("label")).isEqualTo("Order #4711 — ACME");
    }

    @Test
    void attributesAreUnmodifiableAndDefensivelyCopied() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("label", "L");
        EntityRef ref = EntityRef.of("order", "1", source);

        // Mutating the source after construction must not affect the ref.
        source.put("status", "NEW");
        assertThat(ref.attributes()).containsOnlyKeys("label");

        assertThatThrownBy(() -> ref.attributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalityIsByTypeAndIdOnly_ignoringAttributes() {
        EntityRef bare = EntityRef.of("order", "4711");
        EntityRef labelled = EntityRef.of("order", "4711", "label", "Order #4711");

        assertThat(bare).isEqualTo(labelled);
        assertThat(bare.hashCode()).isEqualTo(labelled.hashCode());
    }

    @Test
    void differentIdOrTypeAreNotEqual() {
        assertThat(EntityRef.of("order", "1")).isNotEqualTo(EntityRef.of("order", "2"));
        assertThat(EntityRef.of("order", "1")).isNotEqualTo(EntityRef.of("customer", "1"));
    }

    @Test
    void nullTypeOrIdIsRejected() {
        assertThatThrownBy(() -> EntityRef.of(null, "1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EntityRef.of("order", null)).isInstanceOf(NullPointerException.class);
    }
}
