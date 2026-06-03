package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * End-to-end test of {@link EasyAI#extract(Class)} against a <b>live</b> LLM (Groq).
 *
 * <p>Disabled unless {@code GROQ_API_KEY} is present in the environment, so it never runs in a
 * normal {@code mvn test}/CI build and no key is ever stored in the repo.</p>
 *
 * <p><b>How to run</b> (PowerShell, key passed only at runtime):</p>
 * <pre>
 * $env:GROQ_API_KEY="gsk_..."
 * mvn test "-Dtest=ExtractIntegrationTest"
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class ExtractIntegrationTest {

    enum Priority { LOW, MEDIUM, HIGH, URGENT }

    record LineItem(String description, int quantity, BigDecimal unitPrice) {
    }

    record Invoice(String vendor, String invoiceNumber, LocalDate date,
                   BigDecimal total, Priority priority, List<LineItem> items) {
    }

    private static final String INVOICE_EMAIL = """
            From: billing@acme-corp.com
            Subject: Invoice INV-2026-0042

            Dear customer,

            Please find the details of invoice INV-2026-0042, issued on 4 June 2026 by ACME Corp.

            Items:
              - 3x Mechanical Keyboard at 75.00 EUR each
              - 2x 27" Monitor at 250.00 EUR each

            Total due: 725.00 EUR. This invoice is URGENT and must be paid within 7 days.
            """;

    private <T> ExtractionBuilder<T> groq(Class<T> type) {
        return EasyAI.extract(type)
                .withProvider("openai")                       // Groq is OpenAI-compatible
                .withBaseUrl("https://api.groq.com/openai/v1/")
                .withApiKey(System.getenv("GROQ_API_KEY"))
                .withModel("llama-3.3-70b-versatile");
    }

    @Test
    void extractsInvoiceFromEmailText() {
        Invoice inv = groq(Invoice.class).from(INVOICE_EMAIL);

        assertThat(inv).isNotNull();
        assertThat(inv.vendor()).containsIgnoringCase("ACME");
        assertThat(inv.invoiceNumber()).isEqualTo("INV-2026-0042");
        assertThat(inv.date()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(inv.total()).isEqualByComparingTo("725.00");
        assertThat(inv.priority()).isEqualTo(Priority.URGENT);
        assertThat(inv.items()).hasSize(2);
        assertThat(inv.items()).anySatisfy(item ->
                assertThat(item.description()).containsIgnoringCase("keyboard"));
    }
}
