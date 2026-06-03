package dyntabs.ai;

import java.util.List;

import dyntabs.ai.rag.DocumentSource;
import dyntabs.ai.rag.MilvusConfig;
import dyntabs.ai.rag.MilvusEngine;
import dyntabs.ai.rag.RagEngine;
import dev.langchain4j.data.document.Document;

/**
 * The write side of EasyAI RAG: takes documents and persists their embeddings into a
 * vector store, so an assistant can retrieve them later.
 *
 * <p><b>Analogy:</b> if an {@code @EasyAIAssistant} that reads from a store is the
 * "reader", {@code EasyIndexer} is the "librarian who shelves the books." You hand it
 * raw documents (paths, byte arrays, or plain text); it parses, splits, embeds, and files
 * them away in Milvus under the collection chosen via {@link IndexerBuilder#toMilvus}.
 * One call replaces the parse → split → embed → batch-insert boilerplate you'd otherwise
 * write against the raw Milvus client.</p>
 *
 * <p>You don't construct this directly — it comes from {@link EasyAI#indexer()}:</p>
 * <pre>{@code
 * // Index files on disk / classpath
 * int n = EasyAI.indexer()
 *               .toMilvus("localhost", 19530, "documents")
 *               .index("file:/data/policy.pdf", "classpath:faq.txt");
 *
 * // Index bytes pulled from a DMS or DB BLOB
 * byte[] pdf = dms.download("DOC-1");
 * EasyAI.indexer()
 *       .toMilvus()                                   // settings from easyai.properties
 *       .index(DocumentSource.of("policy.pdf", pdf));
 * }</pre>
 *
 * @see EasyAI#indexer()
 * @see IndexerBuilder
 * @see MilvusEngine
 */
public class EasyIndexer {

    private final MilvusConfig milvusConfig;

    /**
     * Package-private: instances come from {@link IndexerBuilder#toMilvus}.
     *
     * @param milvusConfig the resolved Milvus destination for this indexer
     */
    EasyIndexer(MilvusConfig milvusConfig) {
        this.milvusConfig = milvusConfig;
    }

    /**
     * Indexes documents identified by path strings (classpath, file system, or relative).
     *
     * <p>Terminal step of the {@code EasyAI.indexer().toMilvus(...).index(...)} chain.
     * Delegates loading to {@link RagEngine#loadDocuments(String[])} and persistence to
     * {@link MilvusEngine#ingest(List, MilvusConfig)}.</p>
     *
     * @param sources one or more paths, each optionally prefixed {@code classpath:} or {@code file:}
     * @return the number of documents successfully ingested
     */
    public int index(String... sources) {
        List<Document> documents = RagEngine.loadDocuments(sources);
        return MilvusEngine.ingest(documents, milvusConfig);
    }

    /**
     * Indexes in-memory documents (byte arrays) — for content from a DMS, database BLOB,
     * REST API, or user upload.
     *
     * <p>Terminal step of the indexer chain. Delegates parsing to
     * {@link RagEngine#parseDocumentSources(List)} and persistence to
     * {@link MilvusEngine#ingest(List, MilvusConfig)}.</p>
     *
     * @param sources one or more {@link DocumentSource}s carrying file name + bytes
     * @return the number of documents successfully ingested
     */
    public int index(DocumentSource... sources) {
        return index(List.of(sources));
    }

    /**
     * Indexes a list of in-memory documents (byte arrays). Same behaviour as
     * {@link #index(DocumentSource...)}, for when you already hold a {@code List}.
     *
     * @param sources the documents to ingest
     * @return the number of documents successfully ingested
     */
    public int index(List<DocumentSource> sources) {
        List<Document> documents = RagEngine.parseDocumentSources(sources);
        return MilvusEngine.ingest(documents, milvusConfig);
    }
}
