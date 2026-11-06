package dyntabs.ai.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests how decision settings resolve. Unlike voice, inheriting the chat endpoint here is only right
 * when the chat provider IS OpenAI — these tests pin the behaviour so the documented caveat and the
 * code cannot drift apart.
 */
class DecisionConfigTest {

    private static Properties chatOnly() {
        Properties p = new Properties();
        p.setProperty("easyai.provider", "openai");
        p.setProperty("easyai.api-key", "chat-key");
        p.setProperty("easyai.base-url", "https://api.openai.com/v1/");
        p.setProperty("easyai.model-name", "gpt-4o-mini");
        return p;
    }

    @Test
    @DisplayName("inherits the chat key and base URL")
    void inheritsChatSettings() {
        DecisionConfig config = DecisionConfig.forDeciding(chatOnly());

        assertThat(config.provider()).isEqualTo("openai");
        assertThat(config.apiKey()).isEqualTo("chat-key");
        assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v1/");
    }

    @Test
    @DisplayName("does NOT inherit the chat model — a decision model is a different model")
    void doesNotInheritChatModel() {
        DecisionConfig config = DecisionConfig.forDeciding(chatOnly());

        assertThat(config.modelName()).isEqualTo(DecisionConfig.DEFAULT_MODEL);
        assertThat(config.modelName()).isNotEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName("decision-specific keys override the inherited ones")
    void specificKeysWin() {
        Properties p = chatOnly();
        p.setProperty("easyai.decision.api-key", "decision-key");
        p.setProperty("easyai.decision.base-url", "https://api.openai.com/v2/");
        p.setProperty("easyai.decision.model-name", "some-other-decider");

        DecisionConfig config = DecisionConfig.forDeciding(p);

        assertThat(config.apiKey()).isEqualTo("decision-key");
        assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v2/");
        assertThat(config.modelName()).isEqualTo("some-other-decider");
    }

    @Test
    @DisplayName("a blank value counts as absent, so it cannot shadow a usable fallback")
    void blankValuesAreTreatedAsAbsent() {
        Properties p = chatOnly();
        p.setProperty("easyai.decision.api-key", "  ");
        p.setProperty("easyai.decision.model-name", "");

        DecisionConfig config = DecisionConfig.forDeciding(p);

        assertThat(config.apiKey()).isEqualTo("chat-key");
        assertThat(config.modelName()).isEqualTo(DecisionConfig.DEFAULT_MODEL);
    }

    @Test
    @DisplayName("with no properties at all, defaults apply and no key is invented")
    void emptyPropertiesYieldDefaults() {
        DecisionConfig config = DecisionConfig.forDeciding(new Properties());

        assertThat(config.provider()).isEqualTo("openai");
        assertThat(config.modelName()).isEqualTo(DecisionConfig.DEFAULT_MODEL);
        assertThat(config.apiKey()).isNull();
        assertThat(config.baseUrl()).isNull();
    }

    @Test
    @DisplayName("toBuilder makes a modified copy without touching the original")
    void toBuilderCopies() {
        DecisionConfig original = DecisionConfig.forDeciding(chatOnly());
        DecisionConfig modified = original.toBuilder().apiKey("other").build();

        assertThat(modified.apiKey()).isEqualTo("other");
        assertThat(modified.modelName()).isEqualTo(original.modelName());
        assertThat(original.apiKey()).isEqualTo("chat-key");
    }

    @Test
    @DisplayName("toString never leaks the API key")
    void toStringHidesKey() {
        assertThat(DecisionConfig.forDeciding(chatOnly()).toString())
                .doesNotContain("chat-key")
                .contains("apiKey=present");

        assertThat(DecisionConfig.forDeciding(new Properties()).toString())
                .contains("apiKey=ABSENT");
    }
}
