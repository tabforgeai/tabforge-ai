package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EasyAIExtractErrorTest {

    private static final String GROQ_TOOL_FAILURE =
            "{\"error\":{\"message\":\"Failed to call a function. Please adjust your prompt.\","
            + "\"type\":\"invalid_request_error\",\"code\":\"tool_use_failed\","
            + "\"failed_generation\":\"<function=getOrdersByCustomer {\\\"arg0\\\": \\\"john@example.com\\\"}\"}}";

    private static final String GROQ_AUTH_FAILURE =
            "{\"error\":{\"message\":\"Invalid API key provided.\","
            + "\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}";

    @Test
    void extractsMessageFromOpenAiCompatibleJson() {
        Exception e = new RuntimeException(GROQ_TOOL_FAILURE);
        String msg = EasyAI.extractErrorMessage(e);
        assertThat(msg).isEqualTo("Failed to call a function. Please adjust your prompt.");
    }

    @Test
    void extractsMessageFromAuthError() {
        Exception e = new RuntimeException(GROQ_AUTH_FAILURE);
        String msg = EasyAI.extractErrorMessage(e);
        assertThat(msg).isEqualTo("Invalid API key provided.");
    }

    @Test
    void returnsRawMessageWhenNotJson() {
        Exception e = new RuntimeException("Connection refused");
        String msg = EasyAI.extractErrorMessage(e);
        assertThat(msg).isEqualTo("Connection refused");
    }

    @Test
    void returnsClassNameWhenMessageIsNull() {
        Exception e = new IllegalStateException();
        String msg = EasyAI.extractErrorMessage(e);
        assertThat(msg).isEqualTo("IllegalStateException");
    }

    @Test
    void returnsEmptyStringForNullThrowable() {
        assertThat(EasyAI.extractErrorMessage(null)).isEmpty();
    }

    @Test
    void handlesJsonWithoutErrorField() {
        Exception e = new RuntimeException("{\"status\":\"fail\",\"reason\":\"unknown\"}");
        String msg = EasyAI.extractErrorMessage(e);
        // no "error" field - falls back to raw JSON string
        assertThat(msg).contains("status");
    }
}
