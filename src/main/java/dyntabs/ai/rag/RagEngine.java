package dyntabs.ai.rag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dyntabs.ai.annotation.EasyRAG;

/**
 * RAG engine that loads documents, splits them, embeds them,
 * and provides a {@link ContentRetriever} for use with AI assistants.
 *
 * Uses LangChain4J easy-rag module which includes Tika for PDF/DOCX
 * and a local embedding model.
 */
public final class RagEngine {

    private static final Logger log = LoggerFactory.getLogger(RagEngine.class);

    private RagEngine() {
    }

    /**
     * Creates a {@link ContentRetriever} based on the {@link EasyRAG} annotation.
     */
    public static ContentRetriever createRetriever(EasyRAG ragAnnotation) {
        List<Document> documents = loadDocuments(ragAnnotation.source());

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (!documents.isEmpty()) {
            EmbeddingStoreIngestor.ingest(documents, embeddingStore);
            log.info("RAG: ingested {} document(s) into in-memory embedding store", documents.size());
        } else {
            log.warn("No documents loaded for RAG - assistant will work without document context");
        }

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .maxResults(ragAnnotation.maxResults())
                .minScore(ragAnnotation.minScore())
                .build();
    }

    /**
     * Creates a {@link ContentRetriever} programmatically.
     */
    public static ContentRetriever createRetriever(String[] sources, int maxResults, double minScore) {
        List<Document> documents = loadDocuments(sources);

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (!documents.isEmpty()) {
            EmbeddingStoreIngestor.ingest(documents, embeddingStore);
        }

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    /**
     * Creates a {@link ContentRetriever} from in-memory document sources.
     *
     * <p>Use this when documents come from a DMS, database, REST API, or any
     * source that provides content as {@code byte[]}.</p>
     *
     * @param documentSources the documents as byte arrays
     * @param maxResults      maximum relevant segments to retrieve
     * @param minScore        minimum relevance score (0.0 to 1.0)
     * @return a configured ContentRetriever
     */
    public static ContentRetriever createRetriever(List<DocumentSource> documentSources,
                                                   int maxResults, double minScore) {
        List<Document> documents = parseDocumentSources(documentSources);

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (!documents.isEmpty()) {
            EmbeddingStoreIngestor.ingest(documents, embeddingStore);
            log.info("RAG: ingested {} document(s) from byte sources into in-memory embedding store",
                    documents.size());
        } else {
            log.warn("No documents parsed for RAG - assistant will work without document context");
        }

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    private static List<Document> parseDocumentSources(List<DocumentSource> sources) {
        List<Document> documents = new ArrayList<>();
        ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();

        for (DocumentSource source : sources) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(source.content())) {
                Document doc = parser.parse(bais);
                doc.metadata().put(Document.FILE_NAME, source.fileName());
                documents.add(doc);
                log.debug("Parsed document from bytes: {}", source.fileName());
            } catch (Exception e) {
                log.error("Failed to parse document '{}': {}", source.fileName(), e.getMessage());
            }
        }

        return documents;
    }

    private static List<Document> loadDocuments(String[] sources) {
        List<Document> documents = new ArrayList<>();

        for (String source : sources) {
            try {
                Path path = resolvePath(source);
                Document doc = FileSystemDocumentLoader.loadDocument(path);
                documents.add(doc);
                log.debug("Loaded document: {}", source);
            } catch (Exception e) {
                log.error("Failed to load document '{}': {}", source, e.getMessage());
            }
        }

        return documents;
    }

    private static Path resolvePath(String source) throws IOException {
        if (source.startsWith("classpath:")) {
            String resourcePath = source.substring("classpath:".length());
            URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
            if (url == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("Invalid resource URI: " + url, e);
            }
        } else if (source.startsWith("file:")) {
            return Paths.get(source.substring("file:".length()));
        } else {
            return Paths.get(source);
        }
    }
}
