package io.github.abdellahjebar.paraphe.reviews.domain;

import java.util.Objects;

/** A status a reviewer can give, as configured for a project. */
public record OpinionStatus(String code, String label, OpinionEffect effect) {

    public OpinionStatus {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(effect, "effect");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
