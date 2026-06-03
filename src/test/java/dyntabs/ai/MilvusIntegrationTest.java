package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dyntabs.ai.annotation.EasyAIAssistant;
import dyntabs.ai.rag.DocumentSource;
import dyntabs.ai.rag.MilvusConfig;
import dyntabs.ai.rag.MilvusEngine;

/**
 * End-to-end integration test against a <b>live</b> Milvus instance (and optionally a live
 * LLM). Disabled by default — it only runs when {@code EASYAI_IT=true}, so it never fires
 * during a normal {@code mvn test} or CI build, and never needs secrets baked into the repo.
 *
 * <p><b>How to run</b> (PowerShell, key passed only at runtime — never stored):</p>
 * <pre>
 * $env:EASYAI_IT="true"
 * $env:EASYAI_MILVUS_HOST="localhost"      # default
 * $env:EASYAI_MILVUS_PORT="19530"          # default
 * $env:EASYAI_MILVUS_COLLECTION="easyai_it_demo"
 * $env:GROQ_API_KEY="gsk_..."              # optional, only for the assistant test
 * mvn test "-Dtest=MilvusIntegrationTest"
 * </pre>
 *
 * <p>The round-trip test needs no API key (ingestion + retrieval both use the local
 * embedding model bundled by easy-rag). The assistant test additionally needs
 * {@code GROQ_API_KEY} and is skipped without it.</p>
 */
@EnabledIfEnvironmentVariable(named = "EASYAI_IT", matches = "true")
class MilvusIntegrationTest {

    private static final String POLICY_TEXT =
            "All full-time employees are entitled to 25 vacation days per year. "
            + "Unused vacation days may be carried over to the following year, "
            + "up to a maximum of 5 days.";

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? dflt : v;
    }

    private static MilvusConfig config() {
        return MilvusConfig.of(
                env("EASYAI_MILVUS_HOST", "localhost"),
                Integer.parseInt(env("EASYAI_MILVUS_PORT", "19530")),
                env("EASYAI_MILVUS_COLLECTION", "easyai_it_demo"));
    }

    /**
     * Proves the Milvus round trip independent of any LLM: ingest a known document, then
     * retrieve it back through {@link MilvusEngine#createRetriever}. Retries to absorb
     * Milvus's eventual-consistency window after insert.
     */
    @Test
    void ingestThenRetrieveRoundTrip() throws InterruptedException {
        MilvusConfig cfg = config();

        int ingested = EasyAI.indexer()
                .toMilvus(cfg)
                .index(DocumentSource.ofText("policy", POLICY_TEXT));
        assertThat(ingested).isEqualTo(1);

        ContentRetriever retriever = MilvusEngine.createRetriever(cfg, 3, 0.0);

        String joined = "";
        for (int attempt = 0; attempt < 15; attempt++) {
            List<Content> results =
                    retriever.retrieve(Query.from("How many vacation days do employees get?"));
            joined = results.stream()
                    .map(c -> c.textSegment().text())
                    .reduce("", (a, b) -> a + " " + b);
            if (joined.contains("25")) {
                break;
            }
            Thread.sleep(1000);
        }

        assertThat(joined)
                .as("retrieved segments should contain the indexed vacation-day figure")
                .contains("25");
    }

    /**
     * Full product round trip: ingest into Milvus, then let an EasyAI assistant answer a
     * question using that persistent collection as its retriever, with Groq as the LLM.
     * Skipped unless {@code GROQ_API_KEY} is present.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    void assistantAnswersFromMilvusCollection() {
        MilvusConfig cfg = config();

        EasyAI.indexer().toMilvus(cfg)
                .index(DocumentSource.ofText("policy", POLICY_TEXT));

        EasyAI.configure(EasyAIConfig.builder()
                .provider("openai")                               // Groq is OpenAI-compatible
                .baseUrl("https://api.groq.com/openai/v1/")
                .apiKey(System.getenv("GROQ_API_KEY"))
                .modelName("llama-3.3-70b-versatile")
                .build());

        PolicyBot bot = EasyAI.assistant(PolicyBot.class)
                .withMilvus(cfg)
                .build();

        String answer = bot.ask("How many vacation days do full-time employees get per year?");
        assertThat(answer).contains("25");
    }

    @EasyAIAssistant(systemMessage =
            "Answer strictly based on the provided context. If unsure, say you don't know.")
    interface PolicyBot {
        String ask(String question);
    }
}
