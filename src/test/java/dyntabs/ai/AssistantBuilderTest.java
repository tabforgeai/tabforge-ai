package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dyntabs.ai.annotation.EasyAIAssistant;
import dyntabs.ai.annotation.EasyTool;
import dyntabs.ai.event.EasyAIEvent;
import dyntabs.ai.event.EasyAIEvent.Phase;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;

class AssistantBuilderTest {

    @EasyAIAssistant(systemMessage = "You are a test bot")
    interface TestBot {
        String chat(String message);
    }

    @EasyAIAssistant
    interface SimpleBotNoSystemMessage {
        String chat(String message);
    }

    static class OrderService {
        @EasyTool("Returns the delivery status of an order")
        public String getOrderStatus(String orderId) {
            return "Order " + orderId + " is in transit";
        }
    }

    static class FailingService {
        @EasyTool("Always throws, used to test error feedback")
        public String alwaysFails(String input) {
            throw new RuntimeException("DB connection lost");
        }
    }

    @Test
    void buildsAssistantProxy() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Test response"))
                        .build());

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatModel(mockModel)
                .build();

        assertThat(bot).isNotNull();
        String response = bot.chat("Hello");
        assertThat(response).isEqualTo("Test response");
    }

    @Test
    void readsSystemMessageFromAnnotation() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("OK"))
                        .build());

        // This should not throw - the system message is read from @EasyAIAssistant
        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatModel(mockModel)
                .build();

        assertThat(bot).isNotNull();
    }

    @Test
    void systemMessageCanBeOverridden() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("OK"))
                        .build());

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatModel(mockModel)
                .withSystemMessage("Overridden system message")
                .build();

        assertThat(bot).isNotNull();
    }

    @Test
    void worksWithoutSystemMessage() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("OK"))
                        .build());

        SimpleBotNoSystemMessage bot = EasyAI.assistant(SimpleBotNoSystemMessage.class)
                .withChatModel(mockModel)
                .build();

        assertThat(bot).isNotNull();
        assertThat(bot.chat("Hi")).isEqualTo("OK");
    }

    // -------------------------------------------------------------------------
    // Event stream — withEventListener emits per-tool-call events (Source.ASSISTANT)
    // -------------------------------------------------------------------------

    @Test
    void withEventListener_emitsStepStartedThenStepOnToolCall() {
        List<EasyAIEvent> events = new ArrayList<>();
        ChatModel mockModel = modelThatCallsTool(
                "getOrderStatus", "{\"orderId\": \"42\"}", "Your order is on its way.");

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withTools(new OrderService())
                .withEventListener(events::add)
                .withChatModel(mockModel)
                .build();

        String reply = bot.chat("Where is order 42?");

        assertThat(reply).isEqualTo("Your order is on its way.");
        assertThat(events).allSatisfy(e -> assertThat(e.source()).isEqualTo(Source.ASSISTANT));
        assertThat(events).extracting(EasyAIEvent::phase)
                .containsExactly(Phase.STEP_STARTED, Phase.STEP);
        assertThat(events).extracting(EasyAIEvent::toolName)
                .containsExactly("getOrderStatus", "getOrderStatus");
        assertThat(events.get(1).status()).isEqualTo(Status.SUCCESS);
        assertThat(events.get(1).detail()).contains("42");
    }

    @Test
    void withEventListener_emitsErrorStatusWhenToolThrows() {
        List<EasyAIEvent> events = new ArrayList<>();
        ChatModel mockModel = modelThatCallsTool(
                "alwaysFails", "{\"input\": \"x\"}", "Sorry, that failed.");

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withTools(new FailingService())
                .withEventListener(events::add)
                .withChatModel(mockModel)
                .build();

        bot.chat("Do the failing thing.");

        assertThat(events).extracting(EasyAIEvent::phase)
                .containsExactly(Phase.STEP_STARTED, Phase.STEP);
        assertThat(events.get(1).status()).isEqualTo(Status.ERROR);
        assertThat(events.get(1).detail()).contains("DB connection lost");
    }

    @Test
    void noListener_isSilentAndToolStillRuns() {
        ChatModel mockModel = modelThatCallsTool(
                "getOrderStatus", "{\"orderId\": \"7\"}", "Done.");

        TestBot bot = EasyAI.assistant(TestBot.class)
                .withTools(new OrderService())
                .withChatModel(mockModel)
                .build();

        assertThatCode(() -> assertThat(bot.chat("status of 7?")).isEqualTo("Done."))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Shared memory — withChatMemory keeps the conversation across a rebuild
    // -------------------------------------------------------------------------

    @Test
    void withChatMemory_sharedInstanceSurvivesRebuild() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("one")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("two")).build());

        // The caller owns the memory and keeps it across rebuilds (the tab-aware panel pattern:
        // the tool set changes with the active tab, forcing a rebuild, but the chat must continue).
        ChatMemory shared = MessageWindowChatMemory.withMaxMessages(50);

        TestBot first = EasyAI.assistant(TestBot.class)
                .withChatModel(mockModel)
                .withChatMemory(shared)
                .build();
        first.chat("first question");
        int afterFirst = shared.messages().size();
        assertThat(afterFirst).isGreaterThan(0);

        // Rebuild a brand-new assistant with the SAME memory instance.
        TestBot second = EasyAI.assistant(TestBot.class)
                .withChatModel(mockModel)
                .withChatMemory(shared)
                .build();
        second.chat("second question");

        // The rebuild did NOT reset the conversation — memory kept growing and the earlier
        // exchange is still there.
        assertThat(shared.messages().size()).isGreaterThan(afterFirst);
        assertThat(shared.messages()).anySatisfy(m ->
                assertThat(m.toString()).contains("first question"));
    }

    @Test
    void withChatMemory_nullFallsBackToSizeDefault() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("OK")).build());

        // Passing null must not blow up — it just restores the withMemory(int) default.
        TestBot bot = EasyAI.assistant(TestBot.class)
                .withChatModel(mockModel)
                .withChatMemory(null)
                .build();

        assertThat(bot).isNotNull();
        assertThat(bot.chat("Hi")).isEqualTo("OK");
    }

    /** Mock model that requests one tool call on the first turn, then returns a final answer. */
    private static ChatModel modelThatCallsTool(String toolName, String arguments, String finalAnswer) {
        ChatModel mock = mock(ChatModel.class);
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call-" + toolName).name(toolName).arguments(arguments).build();
        when(mock.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(List.of(toolCall))).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(finalAnswer)).build());
        return mock;
    }
}
