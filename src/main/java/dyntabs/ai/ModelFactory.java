package dyntabs.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Factory that creates {@link ChatLanguageModel} instances based on {@link EasyAIConfig}.
 * Supports OpenAI and Ollama providers.
 */
public final class ModelFactory {

    private ModelFactory() {
    }

    public static ChatLanguageModel create(EasyAIConfig config) {
        String provider = config.provider() != null ? config.provider().toLowerCase() : "openai";

        return switch (provider) {
            case "openai" -> createOpenAI(config);
            case "ollama" -> createOllama(config);
            default -> throw new IllegalArgumentException("Unsupported AI provider: " + provider);
        };
    }

    private static ChatLanguageModel createOpenAI(EasyAIConfig config) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder();

        if (config.apiKey() != null) {
            builder.apiKey(config.apiKey());
        }
        if (config.modelName() != null) {
            builder.modelName(config.modelName());
        }
        if (config.baseUrl() != null) {
            builder.baseUrl(config.baseUrl());
        }
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        if (config.maxTokens() != null) {
            builder.maxTokens(config.maxTokens());
        }

        return builder.build();
    }

    private static ChatLanguageModel createOllama(EasyAIConfig config) {
        // Ollama uses OpenAI-compatible API, so we can reuse the OpenAI model
        // with a different base URL (default: http://localhost:11434/v1/)
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder();

        builder.baseUrl(config.baseUrl() != null ? config.baseUrl() : "http://localhost:11434/v1/");
        builder.apiKey(config.apiKey() != null ? config.apiKey() : "ollama");

        if (config.modelName() != null) {
            builder.modelName(config.modelName());
        }
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        if (config.maxTokens() != null) {
            builder.maxTokens(config.maxTokens());
        }

        return builder.build();
    }
}
