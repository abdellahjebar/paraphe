package io.github.abdellahjebar.paraphe.documents.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.documents.domain.FileTooLargeException;
import io.github.abdellahjebar.paraphe.documents.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    private static final byte[] CONTENT = "%PDF-1.4\nhello\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path root;

    private LocalFileStorage storage() {
        return new LocalFileStorage(root.toString());
    }

    @Test
    void storesAndReadsBackTheSameBytes() throws Exception {
        StoredFile stored = storage().store(new ByteArrayInputStream(CONTENT), 1024);

        try (var in = storage().open(stored.key())) {
            assertThat(in.readAllBytes()).isEqualTo(CONTENT);
        }
        assertThat(stored.sizeBytes()).isEqualTo(CONTENT.length);
    }

    @Test
    void fingerprintIsTheSha256OfTheContent() throws Exception {
        StoredFile stored = storage().store(new ByteArrayInputStream(CONTENT), 1024);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTENT));
        assertThat(stored.sha256()).isEqualTo(expected);
    }

    @Test
    void keyIsGeneratedByTheStorage() throws Exception {
        StoredFile stored = storage().store(new ByteArrayInputStream(CONTENT), 1024);

        assertThat(stored.key()).matches("[0-9a-f-]{36}\\.pdf");
    }

    @Test
    void tooLargeContentIsRefusedAndLeavesNothingBehind() throws Exception {
        assertThatThrownBy(() -> storage().store(new ByteArrayInputStream(CONTENT), 5))
                .isInstanceOf(FileTooLargeException.class);

        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void deleteRemovesTheFileAndIsIdempotent() throws Exception {
        LocalFileStorage storage = storage();
        StoredFile stored = storage.store(new ByteArrayInputStream(CONTENT), 1024);

        storage.delete(stored.key());
        storage.delete(stored.key());

        assertThatThrownBy(() -> storage.open(stored.key())).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void keysThatAreNotGeneratedOnesAreRejected() {
        LocalFileStorage storage = storage();

        for (String key : new String[] {"../secret.pdf", "..\\secret.pdf", "/etc/passwd", "a.pdf", "",
                "00000000-0000-0000-0000-000000000000.exe"}) {
            assertThatThrownBy(() -> storage.open(key))
                    .as("key %s", key)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> storage.open(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyContentIsRefused() {
        assertThatThrownBy(() -> storage().store(new ByteArrayInputStream(new byte[0]), 1024))
                .isInstanceOf(IOException.class);
    }
}
