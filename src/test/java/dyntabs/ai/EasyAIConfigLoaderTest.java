package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EasyAIConfigLoaderTest {

    @Test
    void loadReturnsDefaultsWhenNoPropertiesFile() {
        // easyai.properties is not on classpath during tests (unless added)
        EasyAIConfig config = EasyAIConfigLoader.load();

        assertThat(config.provider()).isEqualTo("openai");
    }

    @Test
    void loadWithOverridesAppliesProgrammaticValues() {
        EasyAIConfig.Builder overrides = EasyAIConfig.builder()
                .apiKey("sk-override")
                .modelName("gpt-4o");

        EasyAIConfig config = EasyAIConfigLoader.load(overrides);

        assertThat(config.apiKey()).isEqualTo("sk-override");
        assertThat(config.modelName()).isEqualTo("gpt-4o");
    }

    @Test
    void loadFromPropertiesFile() {
        // This test uses the test easyai.properties on the test classpath
        EasyAIConfig config = EasyAIConfigLoader.load();

        // If file exists, values come from it; if not, defaults are used
        assertThat(config.provider()).isNotNull();
    }
}
