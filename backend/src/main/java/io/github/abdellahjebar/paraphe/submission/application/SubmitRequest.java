package io.github.abdellahjebar.paraphe.submission.application;

import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;

/**
 * Everything needed to send a new revision for review.
 *
 * @param reviewers who must answer; when null or empty, the default reviewers of the document's work
 *     package are used
 */
public record SubmitRequest(
        DocumentId documentId,
        String label,
        InputStream pdf,
        String originalFileName,
        String externalLink,
        Set<ReviewerId> reviewers,
        String actor) {

    public SubmitRequest {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(actor, "actor");
    }
}
