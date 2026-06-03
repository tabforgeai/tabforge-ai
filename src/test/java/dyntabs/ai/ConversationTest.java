package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

class ConversationTest {

    @Test
    void sendReturnsModelResponse() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Hello! How can I help?"))
                        .build());

        Conversation conversation = new ConversationBuilder()
                .withChatModel(mockModel)
                .build();

        String response = conversation.send("Hello");
        assertThat(response).isEqualTo("Hello! How can I help?");
    }

    @Test
    void sendWithSystemMessage() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("I am a helpful assistant!"))
                        .build());

        Conversation conversation = new ConversationBuilder()
                .withChatModel(mockModel)
                .withSystemMessage("You are a helpful assistant")
                .build();

        String response = conversation.send("Who are you?");
        assertThat(response).isEqualTo("I am a helpful assistant!");
    }

    @Test
    void sendWithMemory() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Response 1"))
                        .build())
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Response 2"))
                        .build());

        Conversation conversation = new ConversationBuilder()
                .withChatModel(mockModel)
                .withMemory(10)
                .build();

        String r1 = conversation.send("First message");
        String r2 = conversation.send("Second message");

        assertThat(r1).isEqualTo("Response 1");
        assertThat(r2).isEqualTo("Response 2");
    }
}
