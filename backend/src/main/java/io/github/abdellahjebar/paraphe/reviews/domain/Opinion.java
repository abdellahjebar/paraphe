package io.github.abdellahjebar.paraphe.reviews.domain;

import java.time.Instant;
import java.util.Objects;

/** A reviewer's answer on one revision: a status, a comment and the moment it was given. */
public record Opinion(ReviewerId reviewerId, OpinionStatus status, String comment, Instant givenAt) {

    public Opinion {
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(givenAt, "givenAt");
        comment = comment == null ? "" : comment;
    }
}
