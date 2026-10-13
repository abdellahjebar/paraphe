package io.github.abdellahjebar.paraphe.reviews.domain;

import java.util.Objects;
import java.util.UUID;

public record ReviewLinkId(UUID value) {

    public ReviewLinkId {
        Objects.requireNonNull(value, "value");
    }

    public static ReviewLinkId random() {
        return new ReviewLinkId(UUID.randomUUID());
    }
}
