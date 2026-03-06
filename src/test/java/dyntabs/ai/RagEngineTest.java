package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dyntabs.ai.annotation.EasyAIAssistant;
import dyntabs.ai.rag.DocumentSource;
import dyntabs.ai.rag.RagEngine;

class RagEngineTest {

    @Test
    void createRetrieverFromClasspathResource() {
        ContentRetriever retriever = RagEngine.createRetriever(
                new String[]{"classpath:test-document.txt"},
                3,
                0.0 // low min score for test
        );

        assertThat(retriever).isNotNull();
    }

    @Test
    void createRetrieverHandlesMissingFile() {
        // Should not throw, just log warning
        ContentRetriever retriever = RagEngine.createRetriever(
                new String[]{"classpath:nonexistent.txt"},
                3,
                0.5
        );

        assertThat(retriever).isNotNull();
    }

    @EasyAIAssistant
    interface TestBot {
        String ask(String question);
    }

    @Test
    void withRAGProgrammaticOnAssistantBuilder() {
        ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Answer from RAG"))
                        .build());

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatLanguageModel(mockModel)
                .withRAG("classpath:test-document.txt")
                .build();

        assertThat(bot).isNotNull();
        String answer = bot.ask("What is EasyAI?");
        assertThat(answer).isNotNull();
    }

    @Test
    void createRetrieverFromByteArray() {
        String textContent = "EasyAI is a Java abstraction layer for AI. "
                + "It supports OpenAI and Ollama providers.";

        ContentRetriever retriever = RagEngine.createRetriever(
                List.of(DocumentSource.ofText("test-doc", textContent)),
                3, 0.0
        );

        assertThat(retriever).isNotNull();
    }

    @Test
    void createRetrieverFromMultipleByteArrays() {
        DocumentSource doc1 = DocumentSource.ofText("policy", "All employees get 25 vacation days per year.");
        DocumentSource doc2 = DocumentSource.ofText("faq", "Q: How do I request time off? A: Use the HR portal.");

        ContentRetriever retriever = RagEngine.createRetriever(
                List.of(doc1, doc2), 5, 0.3
        );

        assertThat(retriever).isNotNull();
    }

    @Test
    void withRAGDocumentSourceOnAssistantBuilder() {
        ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("You get 25 vacation days"))
                        .build());

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatLanguageModel(mockModel)
                .withRAG(DocumentSource.ofText("policy", "All employees get 25 vacation days per year."))
                .build();

        assertThat(bot).isNotNull();
        String answer = bot.ask("How many vacation days do I get?");
        assertThat(answer).isEqualTo("You get 25 vacation days");
    }

    @Test
    void documentSourceOfCreatesFromBytes() {
        byte[] pdfBytes = "fake pdf content".getBytes();
        DocumentSource source = DocumentSource.of("report.pdf", pdfBytes);

        assertThat(source.fileName()).isEqualTo("report.pdf");
        assertThat(source.content()).isEqualTo(pdfBytes);
    }

    @Test
    void documentSourceOfTextCreatesFromString() {
        DocumentSource source = DocumentSource.ofText("policy", "vacation rules here");

        assertThat(source.fileName()).isEqualTo("policy.txt");
        assertThat(source.content()).isNotEmpty();
        assertThat(new String(source.content())).isEqualTo("vacation rules here");
    }
}
