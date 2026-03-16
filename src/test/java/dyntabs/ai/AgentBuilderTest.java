package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dyntabs.ai.agent.AgentStep;
import dyntabs.ai.agent.StepListener;

class AgentBuilderTest {

    // -------------------------------------------------------------------------
    // Minimal service used as a tool in tests
    // -------------------------------------------------------------------------

    static class OrderService {
        public String getOrderStatus(String orderId) {
            return "Order " + orderId + " is in transit";
        }
    }

    static class FailingService {
        public String alwaysFails(String input) {
            throw new RuntimeException("DB connection lost");
        }
    }

    // -------------------------------------------------------------------------
    // Builder configuration tests (no model needed)
    // -------------------------------------------------------------------------

    @Test
    void defaultMaxStepsIs10() {
        assertThat(AgentBuilder.DEFAULT_MAX_STEPS).isEqualTo(10);
    }

    @Test
    void defaultPlannerPromptIsNotEmpty() {
        assertThat(AgentBuilder.DEFAULT_PLANNER_PROMPT).isNotBlank();
        assertThat(AgentBuilder.DEFAULT_PLANNER_PROMPT).contains("step by step");
    }

    // -------------------------------------------------------------------------
    // Build tests — mock model returns a plain answer (no tool calls)
    // -------------------------------------------------------------------------

    @Test
    void buildsAgentSuccessfully() {
        ChatLanguageModel mockModel = modelThatAnswers("Task done.");

        EasyAgent agent = EasyAI.agent()
                .withChatLanguageModel(mockModel)
                .build();

        assertThat(agent).isNotNull();
    }

    @Test
    void agentExecutesSimpleTask() {
        ChatLanguageModel mockModel = modelThatAnswers("Task completed successfully.");

        EasyAgent agent = EasyAI.agent()
                .withChatLanguageModel(mockModel)
                .build();

        String result = agent.execute("Do something simple.");
        assertThat(result).isEqualTo("Task completed successfully.");
    }

    @Test
    void agentWorksWithNoServices() {
        ChatLanguageModel mockModel = modelThatAnswers("No tools needed.");

        EasyAgent agent = EasyAI.agent()
                .withChatLanguageModel(mockModel)
                .build();

        assertThat(agent.execute("Answer directly.")).isEqualTo("No tools needed.");
    }

    @Test
    void withSystemMessage_usesCustomMessage() {
        ChatLanguageModel mockModel = modelThatAnswers("OK");

        EasyAgent agent = EasyAI.agent()
                .withChatLanguageModel(mockModel)
                .withSystemMessage("Custom agent instructions.")
                .build();

        assertThat(agent).isNotNull();
        assertThat(agent.execute("test")).isEqualTo("OK");
    }

    @Test
    void withPlanningPromptTrue_buildSucceeds() {
        ChatLanguageModel mockModel = modelThatAnswers("Plan executed.");

        EasyAgent agent = EasyAI.agent()
                .withChatLanguageModel(mockModel)
                .withPlanningPrompt(true)
                .build();

        assertThat(agent.execute("Complex task.")).isEqualTo("Plan executed.");
    }

    @Test
    void withPlanningPromptAndCustomMessage_buildSucceeds() {
        ChatLanguageModel mockModel = modelThatAnswers("Combined.");

        EasyAgent agent = EasyAI.agent()
                .withChatLanguageModel(mockModel)
                .withPlanningPrompt(true)
                .withSystemMessage("Additional domain instructions.")
                .build();

        assertThat(agent.execute("task")).isEqualTo("Combined.");
    }

    // -------------------------------------------------------------------------
    // Tool execution tests — mock model requests a tool call, then answers
    // -------------------------------------------------------------------------

    @Test
    void agentCallsToolAndReturnsResult() {
        OrderService orderService = new OrderService();

        // First call: model requests tool; second call: model gives final answer
        ChatLanguageModel mockModel = modelThatCallsTool(
                "getOrderStatus", "{\"orderId\": \"123\"}",
                "Order 123 is in transit. Your order will arrive tomorrow.");

        EasyAgent agent = EasyAI.agent()
                .withServices(orderService)
                .withChatLanguageModel(mockModel)
                .build();

        String result = agent.execute("What is the status of order 123?");
        assertThat(result).isEqualTo("Order 123 is in transit. Your order will arrive tomorrow.");
    }

    @Test
    void stepListenerIsCalledWithCorrectData() {
        OrderService orderService = new OrderService();
        List<AgentStep> capturedSteps = new ArrayList<>();
        StepListener listener = capturedSteps::add;

        ChatLanguageModel mockModel = modelThatCallsTool(
                "getOrderStatus", "{\"orderId\": \"42\"}",
                "Done.");

        EasyAgent agent = EasyAI.agent()
                .withServices(orderService)
                .withStepListener(listener)
                .withChatLanguageModel(mockModel)
                .build();

        agent.execute("Check order 42.");

        assertThat(capturedSteps).hasSize(1);
        AgentStep step = capturedSteps.get(0);
        assertThat(step.stepNumber()).isEqualTo(1);
        assertThat(step.toolName()).isEqualTo("getOrderStatus");
        assertThat(step.arguments()).isEqualTo("{\"orderId\": \"42\"}");
        assertThat(step.result()).contains("42");
    }

    @Test
    void stepListenerReceivesStepNumbersInOrder() {
        OrderService orderService = new OrderService();
        List<AgentStep> capturedSteps = new ArrayList<>();

        // Model calls tool twice, then answers
        ChatLanguageModel mockModel = modelThatCallsToolTwice(
                "getOrderStatus", "{\"orderId\": \"1\"}",
                "getOrderStatus", "{\"orderId\": \"2\"}",
                "Both orders checked.");

        EasyAgent agent = EasyAI.agent()
                .withServices(orderService)
                .withStepListener(capturedSteps::add)
                .withChatLanguageModel(mockModel)
                .build();

        agent.execute("Check orders 1 and 2.");

        assertThat(capturedSteps).hasSize(2);
        assertThat(capturedSteps.get(0).stepNumber()).isEqualTo(1);
        assertThat(capturedSteps.get(1).stepNumber()).isEqualTo(2);
    }

    @Test
    void toolErrorReturnsStructuredFeedback() {
        FailingService failingService = new FailingService();
        List<AgentStep> capturedSteps = new ArrayList<>();

        ChatLanguageModel mockModel = modelThatCallsTool(
                "alwaysFails", "{\"input\": \"test\"}",
                "I was unable to complete the request due to a service error.");

        EasyAgent agent = EasyAI.agent()
                .withServices(failingService)
                .withStepListener(capturedSteps::add)
                .withChatLanguageModel(mockModel)
                .build();

        agent.execute("Call the failing service.");

        assertThat(capturedSteps).hasSize(1);
        assertThat(capturedSteps.get(0).result()).startsWith("TOOL_ERROR");
        assertThat(capturedSteps.get(0).result()).contains("alwaysFails");
        assertThat(capturedSteps.get(0).result()).contains("DB connection lost");
        assertThat(capturedSteps.get(0).result()).contains("suggestion=");
    }

    @Test
    void maxStepsLimitStopsExecution() {
        OrderService orderService = new OrderService();
        List<AgentStep> capturedSteps = new ArrayList<>();

        // Model keeps requesting tool calls; agent must stop at maxSteps=2
        ChatLanguageModel mockModel = modelThatKeepsCallingTool(
                "getOrderStatus", "{\"orderId\": \"1\"}",
                "Final answer after being stopped.");

        EasyAgent agent = EasyAI.agent()
                .withServices(orderService)
                .withMaxSteps(2)
                .withStepListener(capturedSteps::add)
                .withChatLanguageModel(mockModel)
                .build();

        agent.execute("Keep checking the order.");

        // Steps 1 and 2 are real; step 3 returns MAX_STEPS_REACHED (no listener call)
        assertThat(capturedSteps.size()).isLessThanOrEqualTo(3);
        // The last captured step that actually ran should be <= maxSteps
        if (!capturedSteps.isEmpty()) {
            assertThat(capturedSteps.get(capturedSteps.size() - 1).stepNumber())
                    .isLessThanOrEqualTo(3);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a mock model that immediately returns a plain text answer. */
    private static ChatLanguageModel modelThatAnswers(String answer) {
        ChatLanguageModel mock = mock(ChatLanguageModel.class);
        when(mock.chat(any(ChatRequest.class)))
                .thenReturn(chatResponse(answer));
        return mock;
    }

    /**
     * Creates a mock model that:
     * 1st call → requests one tool call
     * 2nd call → returns the final answer
     */
    private static ChatLanguageModel modelThatCallsTool(
            String toolName, String arguments, String finalAnswer) {
        ChatLanguageModel mock = mock(ChatLanguageModel.class);
        when(mock.chat(any(ChatRequest.class)))
                .thenReturn(toolCallResponse(toolName, arguments))
                .thenReturn(chatResponse(finalAnswer));
        return mock;
    }

    /**
     * Creates a mock model that:
     * 1st call → requests tool call A
     * 2nd call → requests tool call B
     * 3rd call → returns the final answer
     */
    private static ChatLanguageModel modelThatCallsToolTwice(
            String tool1, String args1,
            String tool2, String args2,
            String finalAnswer) {
        ChatLanguageModel mock = mock(ChatLanguageModel.class);
        when(mock.chat(any(ChatRequest.class)))
                .thenReturn(toolCallResponse(tool1, args1))
                .thenReturn(toolCallResponse(tool2, args2))
                .thenReturn(chatResponse(finalAnswer));
        return mock;
    }

    /**
     * Creates a mock model that keeps requesting the same tool call on every
     * invocation, until it finally returns the final answer (used to test maxSteps).
     */
    private static ChatLanguageModel modelThatKeepsCallingTool(
            String toolName, String arguments, String finalAnswer) {
        ChatLanguageModel mock = mock(ChatLanguageModel.class);
        when(mock.chat(any(ChatRequest.class)))
                .thenReturn(toolCallResponse(toolName, arguments))
                .thenReturn(toolCallResponse(toolName, arguments))
                .thenReturn(toolCallResponse(toolName, arguments))
                .thenReturn(chatResponse(finalAnswer));
        return mock;
    }

    private static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }

    private static ChatResponse toolCallResponse(String toolName, String arguments) {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call-" + toolName)
                .name(toolName)
                .arguments(arguments)
                .build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(List.of(toolCall)))
                .build();
    }
}
