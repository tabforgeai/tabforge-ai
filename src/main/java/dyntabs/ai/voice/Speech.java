package dyntabs.ai.voice;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * One piece of synthesized audio: the <b>bytes</b> plus the <b>MIME type</b> needed to serve them.
 *
 * <h2>Why both halves travel together</h2>
 * <p>A bare {@code byte[]} is not enough to answer an HTTP request: the caller must also set a
 * {@code Content-Type}, and guessing it is how audio ends up silently unplayable in a browser.
 * Keeping the two together means a web endpoint never has to invent the second half:</p>
 *
 * <pre>{@code
 * Speech speech = EasyAI.speak().from("Order ORD-004 has been delivered.");
 * return Response.ok(speech.bytes()).type(speech.mimeType()).build();
 * }</pre>
 *
 * <p>{@link #base64()} is there for the other common destination — embedding the clip straight into
 * a JSON reply or an HTML {@code <audio src="data:...">} attribute.</p>
 *
 * <p><b>Note on the array:</b> {@link #bytes()} hands back the internal array without copying.
 * Audio clips are large enough that defensive copying on every call would be a real cost for a
 * value nobody mutates in practice; treat it as read-only.</p>
 *
 * <p><b>Analogy:</b> a parcel with its shipping label already attached. The contents alone would
 * get stuck at the depot — it is the label that tells the courier what to do with it.</p>
 *
 * @see dyntabs.ai.EasyAI#speak()
 */
public final class Speech {

    private final byte[] bytes;
    private final String mimeType;

    /**
     * @param bytes    the raw audio bytes (required, non-empty)
     * @param mimeType the audio MIME type, e.g. {@code "audio/mpeg"}
     */
    public Speech(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Synthesized audio is empty.");
        }
        this.bytes = bytes;
        this.mimeType = mimeType;
    }

    /** @return the raw audio bytes (not copied — treat as read-only). */
    public byte[] bytes() {
        return bytes;
    }

    /** @return the audio MIME type, suitable for an HTTP {@code Content-Type} header. */
    public String mimeType() {
        return mimeType;
    }

    /** @return the audio as Base64, for a JSON field or a {@code data:} URI. */
    public String base64() {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** @return the clip size in bytes. */
    public int size() {
        return bytes.length;
    }

    /**
     * Write the audio to a stream — e.g. a servlet's or JAX-RS {@code StreamingOutput}'s.
     *
     * @param out the destination stream (not closed by this method)
     * @throws IOException if writing fails
     */
    public void writeTo(OutputStream out) throws IOException {
        out.write(bytes);
    }

    /**
     * Save the audio to a file, creating or truncating it.
     *
     * @param path the destination file
     * @throws IOException if writing fails
     */
    public void saveTo(Path path) throws IOException {
        Files.write(path, bytes);
    }

    @Override
    public String toString() {
        return "Speech[" + bytes.length + " bytes, " + mimeType + "]";
    }
}
