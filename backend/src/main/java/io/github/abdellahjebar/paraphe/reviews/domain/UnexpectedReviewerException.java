package io.github.abdellahjebar.paraphe.reviews.domain;

/** Raised when an opinion comes from a reviewer the revision was not sent to. */
public class UnexpectedReviewerException extends RuntimeException {

    public UnexpectedReviewerException(RevisionId revisionId, ReviewerId reviewerId) {
        super("Reviewer %s is not expected on revision %s".formatted(reviewerId.value(), revisionId.value()));
    }
}
