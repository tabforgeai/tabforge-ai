package dyntabs.ai.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the synthesized-audio value object: it must carry its MIME type and refuse to be empty. */
class SpeechTest {

    private static final byte[] AUDIO = "fake-mp3-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("carries the bytes and the MIME type a web response needs")
    void carriesBytesAndMimeType() {
        Speech speech = new Speech(AUDIO, "audio/mpeg");

        assertThat(speech.bytes()).isEqualTo(AUDIO);
        assertThat(speech.mimeType()).isEqualTo("audio/mpeg");
        assertThat(speech.size()).isEqualTo(AUDIO.length);
    }

    @Test
    @DisplayName("exposes Base64 for a JSON field or a data: URI")
    void exposesBase64() {
        assertThat(new Speech(AUDIO, "audio/mpeg").base64())
                .isEqualTo(Base64.getEncoder().encodeToString(AUDIO));
    }

    @Test
    @DisplayName("writes to a stream, for a servlet or StreamingOutput")
    void writesToStream() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new Speech(AUDIO, "audio/mpeg").writeTo(out);

        assertThat(out.toByteArray()).isEqualTo(AUDIO);
    }

    @Test
    @DisplayName("saves to a file")
    void savesToFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("clip.mp3");

        new Speech(AUDIO, "audio/mpeg").saveTo(file);

        assertThat(Files.readAllBytes(file)).isEqualTo(AUDIO);
    }

    @Test
    @DisplayName("refuses empty audio rather than handing back a silent clip")
    void refusesEmptyAudio() {
        assertThatThrownBy(() -> new Speech(new byte[0], "audio/mpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        assertThatThrownBy(() -> new Speech(null, "audio/mpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString reports size and type, not the payload")
    void toStringIsReadable() {
        assertThat(new Speech(AUDIO, "audio/mpeg").toString())
                .contains(String.valueOf(AUDIO.length))
                .contains("audio/mpeg");
    }
}
