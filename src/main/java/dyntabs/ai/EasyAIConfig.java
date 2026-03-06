package dyntabs.ai;

/**
 * Immutable configuration for EasyAI.
 * Use the {@link Builder} to create instances.
 */
public final class EasyAIConfig {

    private final String provider;
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final Double temperature;
    private final Integer maxTokens;

    private EasyAIConfig(Builder builder) {
        this.provider = builder.provider;
        this.apiKey = builder.apiKey;
        this.modelName = builder.modelName;
        this.baseUrl = builder.baseUrl;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
    }

    public String provider() {
        return provider;
    }

    public String apiKey() {
        return apiKey;
    }

    public String modelName() {
        return modelName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Double temperature() {
        return temperature;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public Builder toBuilder() {
        return new Builder()
                .provider(provider)
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .maxTokens(maxTokens);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String provider = "openai";
        private String apiKey;
        private String modelName;
        private String baseUrl;
        private Double temperature;
        private Integer maxTokens;

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public EasyAIConfig build() {
            return new EasyAIConfig(this);
        }
    }
}
