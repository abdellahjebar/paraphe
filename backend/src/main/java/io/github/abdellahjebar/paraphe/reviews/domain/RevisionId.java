package io.github.abdellahjebar.paraphe.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of one revision (index A, B, C...) of a document. */
public record RevisionId(UUID value) {

    public RevisionId {
        Objects.requireNonNull(value, "value");
    }

    public static RevisionId random() {
        return new RevisionId(UUID.randomUUID());
    }
}
