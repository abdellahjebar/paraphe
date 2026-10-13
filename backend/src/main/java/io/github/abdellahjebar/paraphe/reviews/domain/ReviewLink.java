package io.github.abdellahjebar.paraphe.reviews.domain;

import java.time.Instant;
import java.util.Objects;

/** A personal link: one revision, one reviewer, valid until it expires or is revoked. */
public record ReviewLink(
        ReviewLinkId id,
        RevisionId revisionId,
        ReviewerId reviewerId,
        TokenHash tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant firstOpenedAt,
        Instant revokedAt) {

    public ReviewLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("a link must expire after it is issued");
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Valid until the expiry instant, which itself is already too late. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsableAt(Instant now) {
        return !isRevoked() && !isExpiredAt(now);
    }

    /** Revocation wins over expiry, which wins over what the reviewer did with the link. */
    public LinkStatus statusAt(Instant now, boolean reviewerHasAnswered) {
        if (isRevoked()) {
            return LinkStatus.REVOKED;
        }
        if (isExpiredAt(now)) {
            return LinkStatus.EXPIRED;
        }
        if (reviewerHasAnswered) {
            return LinkStatus.ANSWERED;
        }
        return firstOpenedAt != null ? LinkStatus.OPENED : LinkStatus.ISSUED;
    }
}
