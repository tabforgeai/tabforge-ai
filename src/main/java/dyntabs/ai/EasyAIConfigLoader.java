package dyntabs.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads {@link EasyAIConfig} from {@code easyai.properties} on the classpath.
 * Programmatic overrides take precedence over properties file values.
 */
public final class EasyAIConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(EasyAIConfigLoader.class);
    private static final String PROPERTIES_FILE = "easyai.properties";

    private EasyAIConfigLoader() {
    }

    /**
     * Loads configuration from the properties file only.
     */
    public static EasyAIConfig load() {
        return load(EasyAIConfig.builder());
    }

    /**
     * Loads configuration from the properties file, then applies overrides
     * from the provided builder. Non-null builder values take precedence.
     */
    public static EasyAIConfig load(EasyAIConfig.Builder overrides) {
        Properties props = loadProperties();

        EasyAIConfig.Builder builder = EasyAIConfig.builder();

        String provider = props.getProperty("easyai.provider");
        if (provider != null) {
            builder.provider(provider.trim());
        }

        String apiKey = props.getProperty("easyai.api-key");
        if (apiKey != null) {
            builder.apiKey(apiKey.trim());
        }

        String modelName = props.getProperty("easyai.model-name");
        if (modelName != null) {
            builder.modelName(modelName.trim());
        }

        String baseUrl = props.getProperty("easyai.base-url");
        if (baseUrl != null) {
            builder.baseUrl(baseUrl.trim());
        }

        String temperature = props.getProperty("easyai.temperature");
        if (temperature != null) {
            builder.temperature(Double.parseDouble(temperature.trim()));
        }

        String maxTokens = props.getProperty("easyai.max-tokens");
        if (maxTokens != null) {
            builder.maxTokens(Integer.parseInt(maxTokens.trim()));
        }

        // Apply programmatic overrides
        EasyAIConfig base = builder.build();
        return applyOverrides(base, overrides.build());
    }

    static EasyAIConfig applyOverrides(EasyAIConfig base, EasyAIConfig overrides) {
        EasyAIConfig.Builder merged = base.toBuilder();

        if (overrides.provider() != null) {
            merged.provider(overrides.provider());
        }
        if (overrides.apiKey() != null) {
            merged.apiKey(overrides.apiKey());
        }
        if (overrides.modelName() != null) {
            merged.modelName(overrides.modelName());
        }
        if (overrides.baseUrl() != null) {
            merged.baseUrl(overrides.baseUrl());
        }
        if (overrides.temperature() != null) {
            merged.temperature(overrides.temperature());
        }
        if (overrides.maxTokens() != null) {
            merged.maxTokens(overrides.maxTokens());
        }

        return merged.build();
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                props.load(is);
                log.debug("Loaded EasyAI configuration from {}", PROPERTIES_FILE);
            } else {
                log.debug("{} not found on classpath, using defaults", PROPERTIES_FILE);
            }
        } catch (IOException e) {
            log.warn("Failed to load {}: {}", PROPERTIES_FILE, e.getMessage());
        }
        return props;
    }
}
