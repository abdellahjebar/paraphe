package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;

public class ReviewAlreadyOpenException extends RuntimeException {

    public ReviewAlreadyOpenException(RevisionId revisionId) {
        super("A review is already open for revision %s".formatted(revisionId.value()));
    }
}
