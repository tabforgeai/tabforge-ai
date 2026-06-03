package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dyntabs.ai.rag.MilvusConfig;

/**
 * Unit tests for {@link MilvusConfig}. These are pure (no network): they only exercise
 * the builder, the {@code of(...)} factory, validation, and properties parsing — never a
 * live Milvus connection (which is reserved for manual/integration testing).
 */
class MilvusConfigTest {

    @Test
    void builderAppliesSensibleDefaults() {
        MilvusConfig cfg = MilvusConfig.builder().collectionName("docs").build();

        assertThat(cfg.host()).isEqualTo("localhost");
        assertThat(cfg.port()).isEqualTo(MilvusConfig.DEFAULT_PORT);
        assertThat(cfg.dimension()).isEqualTo(MilvusConfig.DEFAULT_DIMENSION);
        assertThat(cfg.collectionName()).isEqualTo("docs");
        assertThat(cfg.username()).isNull();
        assertThat(cfg.password()).isNull();
    }

    @Test
    void ofSetsHostPortAndCollection() {
        MilvusConfig cfg = MilvusConfig.of("milvus.internal", 19531, "documents");

        assertThat(cfg.host()).isEqualTo("milvus.internal");
        assertThat(cfg.port()).isEqualTo(19531);
        assertThat(cfg.collectionName()).isEqualTo("documents");
        assertThat(cfg.dimension()).isEqualTo(MilvusConfig.DEFAULT_DIMENSION);
    }

    @Test
    void customDimensionAndCredentialsArePreserved() {
        MilvusConfig cfg = MilvusConfig.builder()
                .host("h")
                .port(1234)
                .collectionName("c")
                .dimension(768)
                .username("user")
                .password("secret")
                .build();

        assertThat(cfg.dimension()).isEqualTo(768);
        assertThat(cfg.username()).isEqualTo("user");
        assertThat(cfg.password()).isEqualTo("secret");
    }

    @Test
    void collectionNameIsRequired() {
        assertThatThrownBy(() -> MilvusConfig.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collection");
    }

    @Test
    void blankCollectionNameIsRejected() {
        assertThatThrownBy(() -> MilvusConfig.builder().collectionName("  ").build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromPropertiesWithoutCollectionFailsClearly() {
        // No easyai.properties on the test classpath -> no milvus keys -> collection missing.
        // Verifies fromProperties() tolerates a missing file and surfaces the requirement.
        assertThatThrownBy(MilvusConfig::fromProperties)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collection");
    }
}
