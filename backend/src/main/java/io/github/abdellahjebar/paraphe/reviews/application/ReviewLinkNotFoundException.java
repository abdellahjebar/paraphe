package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;

public class ReviewLinkNotFoundException extends RuntimeException {

    public ReviewLinkNotFoundException(RevisionId revisionId, ReviewerId reviewerId) {
        super("No active link for reviewer %s on revision %s".formatted(reviewerId.value(), revisionId.value()));
    }
}
