package io.github.abdellahjebar.paraphe.documents.domain;

import java.util.Objects;

/** A file kept by the storage, identified by an opaque key and fingerprinted by its SHA-256. */
public record StoredFile(String key, long sizeBytes, String sha256) {

    public StoredFile {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sha256, "sha256");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("a stored file cannot be empty");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
    }
}
