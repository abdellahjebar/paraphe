package io.github.abdellahjebar.paraphe.documents.infrastructure;

import io.github.abdellahjebar.paraphe.documents.domain.FileTooLargeException;
import io.github.abdellahjebar.paraphe.documents.domain.StoredFile;
import io.github.abdellahjebar.paraphe.documents.port.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps files on the local disk. Keys are generated here ({@code <uuid>.pdf}) and checked on every
 * access, so no caller-supplied name can ever reach the file system.
 */
@Component
class LocalFileStorage implements FileStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
    private static final int BUFFER_SIZE = 8192;

    private final Path root;

    LocalFileStorage(@Value("${paraphe.storage.root:./data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(InputStream content, long maxBytes) throws IOException {
        Files.createDirectories(root);
        Path temp = Files.createTempFile(root, "upload-", ".tmp");
        try {
            MessageDigest digest = newDigest();
            long total = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (var out = Files.newOutputStream(temp)) {
                int read;
                while ((read = content.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new FileTooLargeException(maxBytes);
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (total == 0) {
                throw new IOException("empty content");
            }
            String key = UUID.randomUUID() + ".pdf";
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(key, total, HexFormat.of().formatHex(digest.digest()));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (NoSuchFileException ignored) {
            // already gone
        }
    }

    /** Maps a key to its path, refusing anything that is not a key this storage generated. */
    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid storage key");
        }
        Path path = root.resolve(key.substring(0, 2)).resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("invalid storage key");
        }
        return path;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }
}
