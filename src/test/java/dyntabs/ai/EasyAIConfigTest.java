package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EasyAIConfigTest {

    @Test
    void builderCreatesImmutableConfig() {
        EasyAIConfig config = EasyAIConfig.builder()
                .provider("openai")
                .apiKey("sk-test123")
                .modelName("gpt-4o-mini")
                .baseUrl("https://api.openai.com/v1")
                .temperature(0.7)
                .maxTokens(1000)
                .build();

        assertThat(config.provider()).isEqualTo("openai");
        assertThat(config.apiKey()).isEqualTo("sk-test123");
        assertThat(config.modelName()).isEqualTo("gpt-4o-mini");
        assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.temperature()).isEqualTo(0.7);
        assertThat(config.maxTokens()).isEqualTo(1000);
    }

    @Test
    void builderDefaults() {
        EasyAIConfig config = EasyAIConfig.builder().build();

        assertThat(config.provider()).isEqualTo("openai");
        assertThat(config.apiKey()).isNull();
        assertThat(config.modelName()).isNull();
        assertThat(config.baseUrl()).isNull();
        assertThat(config.temperature()).isNull();
        assertThat(config.maxTokens()).isNull();
    }

    @Test
    void toBuilderPreservesValues() {
        EasyAIConfig original = EasyAIConfig.builder()
                .provider("ollama")
                .apiKey("key")
                .modelName("llama3")
                .temperature(0.5)
                .build();

        EasyAIConfig copy = original.toBuilder().build();

        assertThat(copy.provider()).isEqualTo("ollama");
        assertThat(copy.apiKey()).isEqualTo("key");
        assertThat(copy.modelName()).isEqualTo("llama3");
        assertThat(copy.temperature()).isEqualTo(0.5);
    }

    @Test
    void toBuilderAllowsOverride() {
        EasyAIConfig original = EasyAIConfig.builder()
                .provider("openai")
                .modelName("gpt-4o")
                .build();

        EasyAIConfig modified = original.toBuilder()
                .modelName("gpt-4o-mini")
                .build();

        assertThat(modified.provider()).isEqualTo("openai");
        assertThat(modified.modelName()).isEqualTo("gpt-4o-mini");
    }
}
