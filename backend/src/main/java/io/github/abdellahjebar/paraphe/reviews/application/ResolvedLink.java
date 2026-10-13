package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLinkId;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.time.Instant;

/** What a valid token stands for. It carries no secret. */
public record ResolvedLink(ReviewLinkId linkId, RevisionId revisionId, ReviewerId reviewerId, Instant expiresAt) {
}
