package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dyntabs.ai.extract.ExtractionException;
import dyntabs.ai.rag.DocumentSource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Unit tests for {@link EasyAI#extract(Class)} / {@link ExtractionBuilder}, driven by a mock
 * {@link ChatModel} (no network). They cover the happy path, nested/enum/list/date parsing,
 * tolerant JSON extraction (fences + prose), retry behaviour, failure, Bean Validation, and
 * extraction straight from a {@link DocumentSource}.
 */
class ExtractionBuilderTest {

    enum Priority { LOW, MEDIUM, HIGH }

    record LineItem(String description, BigDecimal amount) {
    }

    record Invoice(String vendor, String invoiceNumber, LocalDate date,
                   BigDecimal total, Priority priority, List<LineItem> items) {
    }

    static class Person {
        @NotBlank
        String name;
        @Min(18)
        int age;
    }

    private static final String INVOICE_JSON = """
            {
              "vendor": "ACME Corp",
              "invoiceNumber": "INV-001",
              "date": "2026-06-04",
              "total": 1234.56,
              "priority": "HIGH",
              "items": [ { "description": "Widget", "amount": 10.00 } ]
            }""";

    @Test
    void extractsRecordWithNestedListEnumAndDate() {
        Invoice inv = EasyAI.extract(Invoice.class)
                .withChatModel(modelReturning(INVOICE_JSON))
                .from("Invoice from ACME Corp, number INV-001 ...");

        assertThat(inv.vendor()).isEqualTo("ACME Corp");
        assertThat(inv.invoiceNumber()).isEqualTo("INV-001");
        assertThat(inv.date()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(inv.total()).isEqualByComparingTo("1234.56");
        assertThat(inv.priority()).isEqualTo(Priority.HIGH);
        assertThat(inv.items()).hasSize(1);
        assertThat(inv.items().get(0).description()).isEqualTo("Widget");
    }

    @Test
    void toleratesMarkdownFences() {
        String fenced = "```json\n" + INVOICE_JSON + "\n```";
        Invoice inv = EasyAI.extract(Invoice.class)
                .withChatModel(modelReturning(fenced))
                .from("...");
        assertThat(inv.vendor()).isEqualTo("ACME Corp");
    }

    @Test
    void toleratesSurroundingProse() {
        String chatty = "Sure! Here is the extracted data:\n" + INVOICE_JSON + "\nLet me know if you need more.";
        Invoice inv = EasyAI.extract(Invoice.class)
                .withChatModel(modelReturning(chatty))
                .from("...");
        assertThat(inv.invoiceNumber()).isEqualTo("INV-001");
    }

    @Test
    void retriesOnMalformedJsonThenSucceeds() {
        Invoice inv = EasyAI.extract(Invoice.class)
                .withChatModel(modelReturning("not json at all", INVOICE_JSON))
                .withRetries(1)
                .from("...");
        assertThat(inv.vendor()).isEqualTo("ACME Corp");
    }

    @Test
    void throwsWhenAllAttemptsProduceInvalidJson() {
        assertThatThrownBy(() -> EasyAI.extract(Invoice.class)
                .withChatModel(modelReturning("nope", "still nope"))
                .withRetries(1)
                .from("..."))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Invoice");
    }

    @Test
    void validatePassesForValidObject() {
        Person p = EasyAI.extract(Person.class)
                .withChatModel(modelReturning("{\"name\": \"John\", \"age\": 30}"))
                .validate()
                .from("John is 30");
        assertThat(p.name).isEqualTo("John");
        assertThat(p.age).isEqualTo(30);
    }

    @Test
    void validateThrowsForConstraintViolation() {
        assertThatThrownBy(() -> EasyAI.extract(Person.class)
                .withChatModel(modelReturning("{\"name\": \"\", \"age\": 15}"))
                .validate()
                .from("..."))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("validation");
    }

    @Test
    void extractsFromDocumentSource() {
        Invoice inv = EasyAI.extract(Invoice.class)
                .withChatModel(modelReturning(INVOICE_JSON))
                .from(DocumentSource.ofText("invoice",
                        "Vendor: ACME Corp\nNumber: INV-001\nTotal: 1234.56"));
        assertThat(inv.vendor()).isEqualTo("ACME Corp");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Mock model that returns the given JSON answers in sequence across calls. */
    private static ChatModel modelReturning(String... answers) {
        ChatModel mock = mock(ChatModel.class);
        OngoingStubbing<ChatResponse> stub = when(mock.chat(any(ChatRequest.class)));
        for (String answer : answers) {
            stub = stub.thenReturn(ChatResponse.builder()
                    .aiMessage(AiMessage.from(answer))
                    .build());
        }
        return mock;
    }
}
