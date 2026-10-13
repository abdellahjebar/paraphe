package io.github.abdellahjebar.paraphe.reviews.port;

import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLink;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLinkId;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.TokenHash;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReviewLinkRepository {

    void insert(ReviewLink link);

    Optional<ReviewLink> findByTokenHash(TokenHash hash);

    /** The link that is not revoked for this reviewer and revision, if any. */
    Optional<ReviewLink> findActive(RevisionId revisionId, ReviewerId reviewerId);

    /** Every link ever issued for a revision, oldest first. */
    List<ReviewLink> findByRevision(RevisionId revisionId);

    /** Revokes the active link, if there is one. Returns how many were revoked (0 or 1). */
    int revokeActive(RevisionId revisionId, ReviewerId reviewerId, Instant at);

    /** Records the first opening only; later openings change nothing. */
    void markFirstOpened(ReviewLinkId id, Instant at);
}
