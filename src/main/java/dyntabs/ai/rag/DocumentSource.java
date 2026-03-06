package dyntabs.ai.rag;

/**
 * Represents a document loaded into memory as raw bytes.
 *
 * <p>Use this when your documents come from a DMS, database, REST API,
 * or any other source that provides content as {@code byte[]} rather than
 * as a file on disk.</p>
 *
 * <h3>Use Case 1: Document from a DMS (Document Management System)</h3>
 * <pre>{@code
 * byte[] pdfBytes = dmsClient.downloadDocument("DOC-12345");
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
 *     .withRAG(DocumentSource.of("policy.pdf", pdfBytes))
 *     .build();
 * }</pre>
 *
 * <h3>Use Case 2: Document from a Database (BLOB)</h3>
 * <pre>{@code
 * byte[] content = resultSet.getBytes("document_content");
 * String fileName = resultSet.getString("file_name");
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
 *     .withRAG(DocumentSource.of(fileName, content))
 *     .build();
 * }</pre>
 *
 * <h3>Use Case 3: Multiple Documents from Different Sources</h3>
 * <pre>{@code
 * DocumentSource policy = DocumentSource.of("policy.pdf", dmsClient.download("policy"));
 * DocumentSource faq = DocumentSource.of("faq.txt", restApi.getFaqContent());
 * DocumentSource terms = DocumentSource.of("terms.docx", blobStorage.get("terms"));
 *
 * LegalBot bot = EasyAI.assistant(LegalBot.class)
 *     .withRAG(policy, faq, terms)
 *     .build();
 * }</pre>
 *
 * <h3>Use Case 4: Plain Text Content (No File Needed)</h3>
 * <pre>{@code
 * String textContent = "Company vacation policy: all employees get 25 days...";
 *
 * PolicyBot bot = EasyAI.assistant(PolicyBot.class)
 *     .withRAG(DocumentSource.ofText("policy", textContent))
 *     .build();
 * }</pre>
 *
 * @param fileName the file name with extension (e.g. "policy.pdf", "manual.docx").
 *                 The extension tells the parser how to interpret the bytes.
 * @param content  the raw document content as bytes
 * @see dyntabs.ai.AssistantBuilder#withRAG(DocumentSource...)
 */
public record DocumentSource(
        String fileName,
        byte[] content
) {

    /**
     * Creates a DocumentSource from a file name and raw bytes.
     *
     * <p>The file name extension is important - it tells the document parser
     * how to interpret the bytes (e.g. ".pdf" for PDF parsing, ".docx" for Word).</p>
     *
     * @param fileName file name with extension (e.g. "report.pdf", "manual.docx")
     * @param content  the raw document bytes
     * @return a new DocumentSource
     */
    public static DocumentSource of(String fileName, byte[] content) {
        return new DocumentSource(fileName, content);
    }

    /**
     * Creates a DocumentSource from plain text content.
     *
     * <p>Use this when you already have the text and don't need PDF/DOCX parsing.
     * The text is stored as UTF-8 bytes with a ".txt" extension.</p>
     *
     * @param name    a descriptive name for this content (e.g. "faq", "policy")
     * @param text    the plain text content
     * @return a new DocumentSource
     */
    public static DocumentSource ofText(String name, String text) {
        return new DocumentSource(name + ".txt", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
