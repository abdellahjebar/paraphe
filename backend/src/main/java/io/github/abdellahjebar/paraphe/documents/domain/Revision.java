package io.github.abdellahjebar.paraphe.documents.domain;

import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * One version of a document, labelled by its index ("A", "B", "REV1"...). A revision is a fact: it
 * is never edited, and a newer revision replaces it by having a higher {@code sequence}.
 */
public record Revision(
        RevisionId id,
        DocumentId documentId,
        int sequence,
        String label,
        Instant submittedAt,
        StoredFile file,
        String originalFileName,
        String externalLink) {

    public static final int MAX_LABEL_LENGTH = 50;
    public static final int MAX_LINK_LENGTH = 2048;

    public Revision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(originalFileName, "originalFileName");
        Objects.requireNonNull(label, "label");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence starts at 1");
        }
        label = label.strip();
        if (label.isEmpty()) {
            throw new IllegalArgumentException("revision label must not be blank");
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("revision label is longer than " + MAX_LABEL_LENGTH);
        }
        externalLink = requireHttpUrlOrNull(externalLink);
    }

    private static String requireHttpUrlOrNull(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String candidate = link.strip();
        if (candidate.length() > MAX_LINK_LENGTH) {
            throw new IllegalArgumentException("external link is too long");
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("external link is not a valid URL");
        }
        String scheme = uri.getScheme();
        boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!http || uri.getHost() == null) {
            throw new IllegalArgumentException("external link must be an http or https URL");
        }
        return candidate;
    }
}
