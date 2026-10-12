package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;

public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(RevisionId revisionId) {
        super("No review for revision %s".formatted(revisionId.value()));
    }
}
