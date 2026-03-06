package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dyntabs.ai.annotation.EasyAIAssistant;

class AssistantBuilderTest {

    @EasyAIAssistant(systemMessage = "You are a test bot")
    interface TestBot {
        String chat(String message);
    }

    @EasyAIAssistant
    interface SimpleBotNoSystemMessage {
        String chat(String message);
    }

    @Test
    void buildsAssistantProxy() {
        ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Test response"))
                        .build());

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatLanguageModel(mockModel)
                .build();

        assertThat(bot).isNotNull();
        String response = bot.chat("Hello");
        assertThat(response).isEqualTo("Test response");
    }

    @Test
    void readsSystemMessageFromAnnotation() {
        ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("OK"))
                        .build());

        // This should not throw - the system message is read from @EasyAIAssistant
        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatLanguageModel(mockModel)
                .build();

        assertThat(bot).isNotNull();
    }

    @Test
    void systemMessageCanBeOverridden() {
        ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("OK"))
                        .build());

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatLanguageModel(mockModel)
                .withSystemMessage("Overridden system message")
                .build();

        assertThat(bot).isNotNull();
    }

    @Test
    void worksWithoutSystemMessage() {
        ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("OK"))
                        .build());

        SimpleBotNoSystemMessage bot = EasyAI.assistant(SimpleBotNoSystemMessage.class)
                .withChatLanguageModel(mockModel)
                .build();

        assertThat(bot).isNotNull();
        assertThat(bot.chat("Hi")).isEqualTo("OK");
    }
}
