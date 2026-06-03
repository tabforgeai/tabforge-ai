package dyntabs.ai.rag;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;

/**
 * Bridges EasyAI to a <b>persistent</b> Milvus vector store, the way {@link RagEngine}
 * bridges to an ephemeral in-memory store.
 *
 * <p><b>Analogy:</b> {@link RagEngine} is like an in-memory {@code HashMap} that you
 * rebuild every time the app starts; {@code MilvusEngine} is like a real database table —
 * you write rows once, and they're still there next week for anyone to read. The
 * {@link #ingest} method is the {@code INSERT}, {@link #createRetriever} is the
 * {@code SELECT ... ORDER BY similarity}, and {@link #createStore} is the
 * {@code Connection} both share.</p>
 *
 * <p>Unlike {@link RagEngine}, which rebuilds an {@code InMemoryEmbeddingStore} on every
 * assistant build, a Milvus collection lives outside the JVM: you ingest documents once
 * (write path) and any number of assistants can retrieve from it later (read path).</p>
 *
 * <p>Both paths use the default embedding model provided on the classpath by the
 * {@code langchain4j-easy-rag} module (via {@code ServiceLoader}), so ingestion and
 * retrieval embed with the same model and dimensions line up automatically.</p>
 *
 * @see MilvusConfig
 * @see dyntabs.ai.EasyAI#indexer()
 * @see dyntabs.ai.AssistantBuilder#withMilvus(MilvusConfig)
 */
public final class MilvusEngine {

    private static final Logger log = LoggerFactory.getLogger(MilvusEngine.class);

    private MilvusEngine() {
    }

    /**
     * Opens (creating if necessary) the Milvus collection described by {@code config}
     * and returns it as a LangChain4J {@link EmbeddingStore}.
     *
     * @param config the Milvus connection settings
     * @return a ready-to-use embedding store backed by Milvus
     */
    public static EmbeddingStore<TextSegment> createStore(MilvusConfig config) {
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                .host(config.host())
                .port(config.port())
                .collectionName(config.collectionName())
                .dimension(config.dimension());

        if (config.username() != null) {
            builder.username(config.username());
        }
        if (config.password() != null) {
            builder.password(config.password());
        }

        log.info("Milvus store ready: {}:{} collection='{}' dim={}",
                config.host(), config.port(), config.collectionName(), config.dimension());
        return builder.build();
    }

    /**
     * Ingests already-loaded documents into the Milvus collection: splits each document,
     * embeds the segments, and persists them. Metadata travels with each segment as a
     * single JSON field (the LangChain4J model) and is available for filtering at query time.
     *
     * @param documents the documents to persist (already parsed/loaded)
     * @param config    the target Milvus collection
     * @return the number of documents ingested
     */
    public static int ingest(List<Document> documents, MilvusConfig config) {
        if (documents == null || documents.isEmpty()) {
            log.warn("No documents to ingest into Milvus collection '{}'", config.collectionName());
            return 0;
        }
        EmbeddingStore<TextSegment> store = createStore(config);
        EmbeddingStoreIngestor.ingest(documents, store);
        log.info("Ingested {} document(s) into Milvus collection '{}'",
                documents.size(), config.collectionName());
        return documents.size();
    }

    /**
     * Builds a {@link ContentRetriever} that reads from an existing Milvus collection,
     * for wiring into an AI assistant.
     *
     * @param config     the Milvus collection to retrieve from
     * @param maxResults maximum relevant segments to retrieve per query
     * @param minScore   minimum relevance score (0.0 to 1.0)
     * @return a configured ContentRetriever backed by Milvus
     */
    public static ContentRetriever createRetriever(MilvusConfig config, int maxResults, double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(createStore(config))
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }
}
