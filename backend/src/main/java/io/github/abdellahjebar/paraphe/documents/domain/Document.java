package io.github.abdellahjebar.paraphe.documents.domain;

import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import java.time.Instant;
import java.util.Objects;

/**
 * An execution document (drawing, calculation note, method statement...). The document is the
 * subject; each version of it is a {@link Revision}.
 */
public record Document(
        DocumentId id,
        ProjectId projectId,
        String workPackageCode,
        String title,
        String type,
        Instant createdAt) {

    public static final int MAX_TITLE_LENGTH = 300;
    public static final int MAX_TYPE_LENGTH = 100;

    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(createdAt, "createdAt");
        title = title.strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("document title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("document title is longer than " + MAX_TITLE_LENGTH);
        }
        workPackageCode = blankToNull(workPackageCode);
        type = blankToNull(type);
        if (type != null && type.length() > MAX_TYPE_LENGTH) {
            throw new IllegalArgumentException("document type is longer than " + MAX_TYPE_LENGTH);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
