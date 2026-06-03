package dyntabs.ai.rag;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable connection settings for a Milvus vector database collection.
 *
 * <p><b>Analogy:</b> think of {@code MilvusConfig} as a JDBC connection URL — but for
 * vectors instead of rows. Just as {@code jdbc:postgresql://host:5432/mydb} tells JDBC
 * <i>where the table lives</i>, a {@code MilvusConfig} tells EasyAI where the embedding
 * collection lives (host, port, collection) plus the one extra thing vectors need that
 * SQL tables don't: the embedding {@link #dimension()}.</p>
 *
 * <p>Used by both the ingestion side ({@link dyntabs.ai.EasyAI#indexer()}) and the
 * retrieval side ({@link dyntabs.ai.AssistantBuilder#withMilvus(MilvusConfig)}), so a
 * single {@code MilvusConfig} can describe "where the vectors live" for the whole
 * round trip.</p>
 *
 * <h3>Create explicitly</h3>
 * <pre>{@code
 * MilvusConfig cfg = MilvusConfig.of("localhost", 19530, "documents");
 * }</pre>
 *
 * <h3>Or read from {@code easyai.properties}</h3>
 * <pre>
 * easyai.milvus.host=localhost
 * easyai.milvus.port=19530
 * easyai.milvus.collection=documents
 * easyai.milvus.dimension=384
 * </pre>
 * <pre>{@code
 * MilvusConfig cfg = MilvusConfig.fromProperties();
 * }</pre>
 *
 * <p>The default {@link #dimension()} is {@value #DEFAULT_DIMENSION}, matching the local
 * embedding model bundled with the {@code langchain4j-easy-rag} module (and the widely
 * used {@code all-MiniLM-L6-v2}). If you embed with a different model, set the matching
 * dimension or Milvus will reject the inserts.</p>
 */
public final class MilvusConfig {

    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);
    private static final String PROPERTIES_FILE = "easyai.properties";

    /** Default Milvus gRPC port. */
    public static final int DEFAULT_PORT = 19530;
    /** Default embedding dimension (matches the easy-rag local model / all-MiniLM-L6-v2). */
    public static final int DEFAULT_DIMENSION = 384;

    private final String host;
    private final int port;
    private final String collectionName;
    private final int dimension;
    private final String username;
    private final String password;

    private MilvusConfig(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.collectionName = b.collectionName;
        this.dimension = b.dimension;
        this.username = b.username;
        this.password = b.password;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String collectionName() {
        return collectionName;
    }

    public int dimension() {
        return dimension;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    /**
     * Convenience factory for the common case: host, port, and collection name,
     * with the default embedding {@link #dimension()}.
     *
     * @param host           Milvus server hostname
     * @param port           Milvus server port (typically {@value #DEFAULT_PORT})
     * @param collectionName the target collection
     * @return a new MilvusConfig
     */
    public static MilvusConfig of(String host, int port, String collectionName) {
        return builder().host(host).port(port).collectionName(collectionName).build();
    }

    /**
     * Loads Milvus settings from {@code easyai.properties} on the classpath.
     *
     * <p>Recognised keys: {@code easyai.milvus.host}, {@code easyai.milvus.port},
     * {@code easyai.milvus.collection}, {@code easyai.milvus.dimension},
     * {@code easyai.milvus.username}, {@code easyai.milvus.password}.</p>
     *
     * @return a MilvusConfig populated from the properties file (with defaults applied)
     */
    public static MilvusConfig fromProperties() {
        Properties props = new Properties();
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                props.load(is);
            } else {
                log.debug("{} not found on classpath; Milvus config relies on defaults", PROPERTIES_FILE);
            }
        } catch (IOException e) {
            log.warn("Failed to load {} for Milvus config: {}", PROPERTIES_FILE, e.getMessage());
        }

        Builder b = builder();
        String host = props.getProperty("easyai.milvus.host");
        if (host != null) {
            b.host(host.trim());
        }
        String port = props.getProperty("easyai.milvus.port");
        if (port != null) {
            b.port(Integer.parseInt(port.trim()));
        }
        String collection = props.getProperty("easyai.milvus.collection");
        if (collection != null) {
            b.collectionName(collection.trim());
        }
        String dimension = props.getProperty("easyai.milvus.dimension");
        if (dimension != null) {
            b.dimension(Integer.parseInt(dimension.trim()));
        }
        String username = props.getProperty("easyai.milvus.username");
        if (username != null) {
            b.username(username.trim());
        }
        String password = props.getProperty("easyai.milvus.password");
        if (password != null) {
            b.password(password.trim());
        }
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = "localhost";
        private int port = DEFAULT_PORT;
        private String collectionName;
        private int dimension = DEFAULT_DIMENSION;
        private String username;
        private String password;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public MilvusConfig build() {
            if (collectionName == null || collectionName.isBlank()) {
                throw new IllegalStateException(
                        "Milvus collection name is required. Set it via MilvusConfig.of(...), "
                        + "the builder, or 'easyai.milvus.collection' in easyai.properties.");
            }
            return new MilvusConfig(this);
        }
    }
}
