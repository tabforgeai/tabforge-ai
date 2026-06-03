package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dyntabs.ai.extract.SchemaDescriber;

/**
 * Unit tests for {@link SchemaDescriber}. Pure reflection, no model involved: they assert the
 * generated JSON skeleton names the fields and hints the right types, including nested
 * objects, lists, enums, and {@code java.time} values.
 */
class SchemaDescriberTest {

    enum Priority { LOW, MEDIUM, HIGH }

    record LineItem(String description, BigDecimal amount) {
    }

    record Invoice(String vendor, String invoiceNumber, LocalDate date,
                   BigDecimal total, Priority priority, List<LineItem> items) {
    }

    @Test
    void describesScalarFieldsWithTypeHints() {
        String schema = SchemaDescriber.describe(Invoice.class);

        assertThat(schema).contains("\"vendor\"");
        assertThat(schema).contains("\"invoiceNumber\"");
        assertThat(schema).contains("\"total\": \"number\"");
    }

    @Test
    void describesDateAsIso8601() {
        String schema = SchemaDescriber.describe(Invoice.class);
        assertThat(schema).contains("ISO-8601");
    }

    @Test
    void describesEnumByListingAllowedValues() {
        String schema = SchemaDescriber.describe(Invoice.class);
        assertThat(schema).contains("one of");
        assertThat(schema).contains("LOW");
        assertThat(schema).contains("HIGH");
    }

    @Test
    void describesNestedListWithElementShape() {
        String schema = SchemaDescriber.describe(Invoice.class);

        // list bracket + nested element fields
        assertThat(schema).contains("[");
        assertThat(schema).contains("\"description\"");
        assertThat(schema).contains("\"amount\": \"number\"");
    }
}
