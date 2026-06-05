package dyntabs.ai;

import dyntabs.ai.event.EasyAIListener;
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

    private EasyAIListener eventListener;

    /**
     * Package-private: instances come from {@link EasyAI#indexer()}.
     */
    IndexerBuilder() {
    }

    /**
     * Registers a listener that receives a live {@link dyntabs.ai.event.EasyAIEvent} stream as
     * documents are loaded, embedded, and upserted into the vector store.
     *
     * <p>Call this <em>before</em> {@code toMilvus(...)} so the destination indexer inherits it.
     * The indexer emits {@link dyntabs.ai.event.EasyAIEvent.Phase#STARTED} when indexing begins,
     * a {@link dyntabs.ai.event.EasyAIEvent.Phase#PROGRESS} event per source document and one for
     * the store-upsert step, then {@link dyntabs.ai.event.EasyAIEvent.Phase#FINISHED} (or
     * {@link dyntabs.ai.event.EasyAIEvent.Phase#ERROR}).</p>
     *
     * <p><b>Familiar analogy:</b> a parcel-tracking page for a bulk shipment — instead of only
     * learning "all 200 boxes delivered" at the end, you watch each box move through the depot.</p>
     *
     * <pre>{@code
     * int n = EasyAI.indexer()
     *               .withEventListener(e -> log.info("{}", e))
     *               .toMilvus("localhost", 19530, "documents")
     *               .index("file:/data/policy.pdf", "classpath:faq.txt");
     * }</pre>
     *
     * @param eventListener the listener to receive ingestion events (may be {@code null})
     * @return this builder
     * @see dyntabs.ai.event.EasyAIListener
     */
    public IndexerBuilder withEventListener(EasyAIListener eventListener) {
        this.eventListener = eventListener;
        return this;
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
        return new EasyIndexer(MilvusConfig.of(host, port, collectionName), eventListener);
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
        return new EasyIndexer(config, eventListener);
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
        return new EasyIndexer(MilvusConfig.fromProperties(), eventListener);
    }
}
