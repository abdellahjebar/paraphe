package io.github.abdellahjebar.paraphe.documents.domain;

import java.util.Objects;
import java.util.UUID;

public record DocumentId(UUID value) {

    public DocumentId {
        Objects.requireNonNull(value, "value");
    }

    public static DocumentId random() {
        return new DocumentId(UUID.randomUUID());
    }
}
