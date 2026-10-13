package io.github.abdellahjebar.paraphe.reviews.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A reviewer's answer on one revision: a status, a comment and the moment it was given.
 *
 * <p>{@code enteredBy} is empty when the reviewer answered through their own link, and names the
 * person who typed it when the answer was recorded on the reviewer's behalf.
 */
public record Opinion(ReviewerId reviewerId, OpinionStatus status, String comment, Instant givenAt,
                      String enteredBy) {

    public Opinion {
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(givenAt, "givenAt");
        comment = comment == null ? "" : comment;
        enteredBy = enteredBy == null || enteredBy.isBlank() ? null : enteredBy.strip();
    }

    /** An opinion given by the reviewer themselves. */
    public Opinion(ReviewerId reviewerId, OpinionStatus status, String comment, Instant givenAt) {
        this(reviewerId, status, comment, givenAt, null);
    }

    public boolean isOnBehalf() {
        return enteredBy != null;
    }
}
