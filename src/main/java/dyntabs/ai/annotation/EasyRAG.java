package dyntabs.ai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables RAG (Retrieval-Augmented Generation) for an AI assistant.
 *
 * <p>RAG lets the AI answer questions based on <b>your documents</b> (PDF, DOCX, TXT, etc.)
 * instead of relying only on its general knowledge. The documents are loaded, split into
 * segments, and embedded at build time. When the user asks a question, EasyAI automatically
 * finds the most relevant segments and includes them in the AI prompt.</p>
 *
 * <h2>Two Ways to Provide Documents</h2>
 *
 * <p>EasyAI supports two approaches for loading documents into RAG:</p>
 * <ol>
 *   <li><b>This annotation ({@code @EasyRAG})</b> - for documents with known, fixed paths
 *       (classpath resources or static files). Simple, declarative, zero code.</li>
 *   <li><b>Programmatic via {@link dyntabs.ai.AssistantBuilder#withRAG(dyntabs.ai.rag.DocumentSource...)
 *       withRAG(DocumentSource...)}</b> - for documents loaded at runtime as {@code byte[]}
 *       from a DMS, database BLOB, REST API, user upload, or any other dynamic source.</li>
 * </ol>
 *
 * <h2>Approach 1: {@code @EasyRAG} Annotation (Static Paths)</h2>
 *
 * <h3>Use Case 1: Company Policy Bot</h3>
 * <pre>{@code
 * @EasyRAG(source = "classpath:company-policy.pdf")
 * @EasyAIAssistant(systemMessage = "Answer questions based on the company policy document")
 * public interface PolicyBot {
 *     String ask(String question);
 * }
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class).build();
 * String answer = bot.ask("How many vacation days do I get?");
 * // AI reads the PDF and gives an answer based on the actual policy
 * }</pre>
 *
 * <h3>Use Case 2: Multiple Static Documents</h3>
 * <pre>{@code
 * @EasyRAG(source = {
 *     "classpath:docs/installation-guide.pdf",
 *     "classpath:docs/user-manual.pdf",
 *     "classpath:docs/faq.txt"
 * })
 * @EasyAIAssistant(systemMessage = "Help users with product questions using the documentation")
 * public interface HelpDeskBot {
 *     String ask(String question);
 * }
 * }</pre>
 *
 * <h3>Use Case 3: Loading From File System</h3>
 * <pre>{@code
 * @EasyRAG(source = "file:C:/data/legal-terms.pdf", maxResults = 5, minScore = 0.7)
 * @EasyAIAssistant
 * public interface LegalBot {
 *     String ask(String question);
 * }
 * }</pre>
 *
 * <h2>Approach 2: Programmatic {@code withRAG(DocumentSource...)} (Dynamic Content)</h2>
 *
 * <p>When documents are not static files but come from external systems at runtime,
 * skip this annotation and use
 * {@link dyntabs.ai.AssistantBuilder#withRAG(dyntabs.ai.rag.DocumentSource...)
 * AssistantBuilder.withRAG(DocumentSource...)} instead.
 * This is typical in enterprise/web applications where documents live in a DMS,
 * database, or are uploaded by users.</p>
 *
 * <h3>Use Case 4: Document from a DMS (byte[])</h3>
 * <pre>{@code
 * byte[] pdfBytes = dmsClient.downloadDocument("DOC-12345");
 *
 * @EasyAIAssistant(systemMessage = "Answer based on the policy document")
 * public interface PolicyBot {
 *     String ask(String question);
 * }
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
 *     .withRAG(DocumentSource.of("policy.pdf", pdfBytes))
 *     .build();
 * }</pre>
 *
 * <h3>Use Case 5: Document from a Database BLOB</h3>
 * <pre>{@code
 * byte[] content = resultSet.getBytes("document_content");
 * String fileName = resultSet.getString("file_name");
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
 *     .withRAG(DocumentSource.of(fileName, content))
 *     .build();
 * }</pre>
 *
 * <h3>Use Case 6: Plain Text (No File at All)</h3>
 * <pre>{@code
 * String rulesText = configService.loadBusinessRules();
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
 *     .withRAG(DocumentSource.ofText("rules", rulesText))
 *     .build();
 * }</pre>
 *
 * <h3>Use Case 7: Multiple Documents from Mixed Sources</h3>
 * <pre>{@code
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
 *     .withRAG(
 *         DocumentSource.of("policy.pdf", dmsClient.download("policy")),
 *         DocumentSource.of("terms.docx", blobStorage.get("terms")),
 *         DocumentSource.ofText("extra-rules", additionalRulesText)
 *     )
 *     .build();
 * }</pre>
 *
 * <h2>How Source Paths Work (for {@code @EasyRAG} annotation)</h2>
 * <ul>
 *   <li>{@code "classpath:docs/file.pdf"} - loads from the classpath (inside your JAR/WAR)</li>
 *   <li>{@code "file:C:/data/file.pdf"} - loads from the file system (absolute path)</li>
 *   <li>{@code "relative/path.txt"} - loads from the current working directory</li>
 * </ul>
 *
 * <h2>Supported File Formats</h2>
 * <p>PDF, DOCX, DOC, PPTX, TXT, HTML, and more (via Apache Tika, included automatically).
 * This applies to both annotation-based and {@code DocumentSource}-based approaches.</p>
 *
 * <h2>Tuning Parameters</h2>
 * <ul>
 *   <li>{@link #maxResults()} - More results = more context for AI, but higher cost.
 *       Default 3 is good for most cases.</li>
 *   <li>{@link #minScore()} - Higher = only very relevant segments are included.
 *       Lower = more segments but possibly less relevant. Default 0.5 works well.</li>
 * </ul>
 *
 * @see EasyAIAssistant
 * @see dyntabs.ai.rag.RagEngine
 * @see dyntabs.ai.rag.DocumentSource
 * @see dyntabs.ai.AssistantBuilder#withRAG(dyntabs.ai.rag.DocumentSource...)
 * @see dyntabs.ai.AssistantBuilder#withRAG(String...)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyRAG {

    /**
     * Document source paths.
     *
     * <p>Supports prefixes:</p>
     * <ul>
     *   <li>{@code classpath:} - classpath resource (e.g. "classpath:docs/manual.pdf")</li>
     *   <li>{@code file:} - absolute file path (e.g. "file:C:/data/terms.pdf")</li>
     *   <li>no prefix - relative to working directory</li>
     * </ul>
     *
     * @return one or more document paths
     */
    String[] source();

    /**
     * Maximum number of relevant document segments to include in the AI prompt.
     *
     * <p>When the user asks a question, EasyAI finds the top N most relevant segments
     * from your documents. More segments = more context for the AI, but also higher
     * token cost and potentially slower responses.</p>
     *
     * @return max number of segments (default 3)
     */
    int maxResults() default 3;

    /**
     * Minimum relevance score for a document segment to be included (0.0 to 1.0).
     *
     * <p>Segments with a similarity score below this threshold are discarded.</p>
     * <ul>
     *   <li>0.0 = include everything (even barely related segments)</li>
     *   <li>0.5 = balanced (default, good for most cases)</li>
     *   <li>0.8 = strict (only very relevant segments)</li>
     * </ul>
     *
     * @return minimum score (default 0.5)
     */
    double minScore() default 0.5;
}
