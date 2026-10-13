package io.github.abdellahjebar.paraphe.submission.application;

import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.reviews.application.IssuedLink;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import java.util.List;

/** A revision that was stored and sent for review, with the one link to give each reviewer. */
public record SubmittedRevision(Revision revision, List<ReviewerLink> links) {

    /** The link of one reviewer. Its token cannot be recovered later, so hand it over now. */
    public record ReviewerLink(ReviewerId reviewerId, IssuedLink link) {
    }
}
