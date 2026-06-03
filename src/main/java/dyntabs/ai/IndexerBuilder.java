package dyntabs.ai;

import dyntabs.ai.rag.MilvusConfig;

/**
 * Entry step for the document-indexing pipeline — picks <i>where</i> the vectors go,
 * then hands you an {@link EasyIndexer} that knows <i>how</i> to put them there.
 *
 * <p><b>Analogy:</b> this is the "choose your destination" screen. Like calling
 * {@code DriverManager.getConnection(url)} before you can run any SQL, you first say
 * {@code .toMilvus(...)} to get something you can actually {@code index(...)} into.
 * Separating the two steps keeps the door open for future destinations (pgvector,
 * Chroma, ...) without changing how callers start the chain.</p>
 *
 * <p>You never construct this directly — start from {@link EasyAI#indexer()}:</p>
 * <pre>{@code
 * EasyAI.indexer()
 *       .toMilvus("localhost", 19530, "documents")
 *       .index("file:/data/policy.pdf");
 * }</pre>
 *
 * @see EasyAI#indexer()
 * @see EasyIndexer
 * @see dyntabs.ai.rag.MilvusConfig
 */
public class IndexerBuilder {

    /**
     * Package-private: instances come from {@link EasyAI#indexer()}.
     */
    IndexerBuilder() {
    }

    /**
     * Targets a Milvus collection using explicit connection settings, with the default
     * embedding {@link MilvusConfig#DEFAULT_DIMENSION dimension} (384).
     *
     * <p>Called as the first link after {@link EasyAI#indexer()}; returns the configured
     * {@link EasyIndexer} on which you call {@code index(...)}.</p>
     *
     * @param host           Milvus server hostname (e.g. {@code "localhost"})
     * @param port           Milvus server port (typically {@value dyntabs.ai.rag.MilvusConfig#DEFAULT_PORT})
     * @param collectionName the target collection
     * @return an {@link EasyIndexer} bound to that collection
     */
    public EasyIndexer toMilvus(String host, int port, String collectionName) {
        return new EasyIndexer(MilvusConfig.of(host, port, collectionName));
    }

    /**
     * Targets a Milvus collection using a fully built {@link MilvusConfig}.
     *
     * <p>Use this overload when you need a non-default dimension or credentials.</p>
     *
     * @param config the Milvus connection settings
     * @return an {@link EasyIndexer} bound to that collection
     */
    public EasyIndexer toMilvus(MilvusConfig config) {
        return new EasyIndexer(config);
    }

    /**
     * Targets a Milvus collection configured entirely from {@code easyai.properties}
     * (keys {@code easyai.milvus.*}).
     *
     * <p>The zero-argument, "it's all in config" path — ideal for Jakarta EE apps that
     * keep environment settings out of code.</p>
     *
     * @return an {@link EasyIndexer} bound to the collection described in properties
     * @see MilvusConfig#fromProperties()
     */
    public EasyIndexer toMilvus() {
        return new EasyIndexer(MilvusConfig.fromProperties());
    }
}
