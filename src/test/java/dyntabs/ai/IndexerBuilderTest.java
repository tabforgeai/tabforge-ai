package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dyntabs.ai.annotation.EasyAIAssistant;
import dyntabs.ai.rag.MilvusConfig;

/**
 * Wiring tests for the indexer chain and the assistant's {@code withMilvus(...)} option.
 *
 * <p>These verify the fluent chain assembles correctly <b>without</b> opening a Milvus
 * connection: {@code toMilvus(...)} only resolves a {@link MilvusConfig}, and the actual
 * connect happens later (on {@code index(...)} or assistant {@code build()}), which needs
 * a running server and is therefore out of scope for unit tests.</p>
 */
class IndexerBuilderTest {

    @EasyAIAssistant
    interface TestBot {
        String ask(String question);
    }

    @Test
    void indexerReturnsBuilder() {
        assertThat(EasyAI.indexer()).isNotNull();
    }

    @Test
    void toMilvusWithHostPortCollectionReturnsIndexer() {
        EasyIndexer indexer = EasyAI.indexer().toMilvus("localhost", 19530, "documents");
        assertThat(indexer).isNotNull();
    }

    @Test
    void toMilvusWithConfigReturnsIndexer() {
        MilvusConfig cfg = MilvusConfig.of("localhost", 19530, "documents");
        EasyIndexer indexer = EasyAI.indexer().toMilvus(cfg);
        assertThat(indexer).isNotNull();
    }

    @Test
    void assistantWithMilvusIsFluent() {
        AssistantBuilder<TestBot> builder = EasyAI.assistant(TestBot.class);
        AssistantBuilder<TestBot> returned = builder.withMilvus("localhost", 19530, "documents");
        assertThat(returned).isSameAs(builder);
    }

    @Test
    void assistantWithMilvusConfigAndTuningIsFluent() {
        MilvusConfig cfg = MilvusConfig.of("localhost", 19530, "documents");
        AssistantBuilder<TestBot> builder = EasyAI.assistant(TestBot.class);
        assertThat(builder.withMilvus(cfg, 5, 0.7)).isSameAs(builder);
    }
}
