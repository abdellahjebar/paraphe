package io.github.abdellahjebar.paraphe.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a project participant who gives opinions. */
public record ReviewerId(UUID value) {

    public ReviewerId {
        Objects.requireNonNull(value, "value");
    }

    public static ReviewerId random() {
        return new ReviewerId(UUID.randomUUID());
    }
}
