package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.reviews.domain.LinkStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLink;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLinkId;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewToken;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionReview;
import io.github.abdellahjebar.paraphe.reviews.domain.TokenHash;
import io.github.abdellahjebar.paraphe.reviews.domain.UnexpectedReviewerException;
import io.github.abdellahjebar.paraphe.reviews.port.ReviewLinkRepository;
import io.github.abdellahjebar.paraphe.reviews.port.ReviewRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, revokes and resolves the personal links reviewers use to answer.
 *
 * <p>Resolving never throws for a bad token: it returns empty, so that the rejection event written
 * to the journal is not rolled back with an exception. Callers answer "not found" for every empty
 * result, whatever the reason, so a link's state is not revealed to whoever holds it.
 */
@Service
@Transactional
public class ReviewLinkService {

    private static final String TARGET_TYPE = "REVISION";

    private final ReviewLinkRepository links;
    private final ReviewRepository reviews;
    private final AuditLog auditLog;
    private final Clock clock;
    private final Duration timeToLive;

    ReviewLinkService(ReviewLinkRepository links, ReviewRepository reviews, AuditLog auditLog, Clock clock,
                      @Value("${paraphe.review-link.ttl-days:30}") long ttlDays) {
        this.links = links;
        this.reviews = reviews;
        this.auditLog = auditLog;
        this.clock = clock;
        this.timeToLive = Duration.ofDays(ttlDays);
    }

    /**
     * Issues a link, revoking the reviewer's previous one for this revision. The reviewer must be one
     * of the expected reviewers of an open review.
     */
    public IssuedLink issue(RevisionId revisionId, ReviewerId reviewerId, String actor) {
        RevisionReview review = reviews.find(revisionId).orElseThrow(() -> new ReviewNotFoundException(revisionId));
        if (!review.expected().contains(reviewerId)) {
            throw new UnexpectedReviewerException(revisionId, reviewerId);
        }

        Instant now = clock.instant();
        links.revokeActive(revisionId, reviewerId, now);

        ReviewToken token = ReviewToken.generate();
        ReviewLink link = new ReviewLink(ReviewLinkId.random(), revisionId, reviewerId, token.hash(),
                now, now.plus(timeToLive), null, null);
        links.insert(link);

        record("ReviewLinkIssued", actor, revisionId,
                JsonPayload.of("reviewerId", reviewerId.value().toString(),
                        "linkId", link.id().value().toString(),
                        "expiresAt", link.expiresAt().toString()), now);
        return new IssuedLink(link.id(), token, link.expiresAt());
    }

    /** Revokes the reviewer's active link for this revision. */
    public void revoke(RevisionId revisionId, ReviewerId reviewerId, String actor) {
        Instant now = clock.instant();
        if (links.revokeActive(revisionId, reviewerId, now) == 0) {
            throw new ReviewLinkNotFoundException(revisionId, reviewerId);
        }
        record("ReviewLinkRevoked", actor, revisionId,
                JsonPayload.of("reviewerId", reviewerId.value().toString()), now);
    }

    /**
     * Looks a token up without any other effect. Empty means "not usable": unknown, expired or
     * revoked. A known link that is no longer usable leaves a rejection event.
     */
    public Optional<ResolvedLink> resolve(String rawToken) {
        if (!ReviewToken.hasTokenShape(rawToken)) {
            return Optional.empty();
        }
        Optional<ReviewLink> found = links.findByTokenHash(TokenHash.of(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ReviewLink link = found.get();
        Instant now = clock.instant();
        if (!link.isUsableAt(now)) {
            String reason = link.isRevoked() ? "REVOKED" : "EXPIRED";
            record("ReviewLinkRejected", "reviewer:" + link.reviewerId().value(), link.revisionId(),
                    JsonPayload.of("reason", reason, "linkId", link.id().value().toString()), now);
            return Optional.empty();
        }
        return Optional.of(new ResolvedLink(link.id(), link.revisionId(), link.reviewerId(), link.expiresAt()));
    }

    /** Resolves a token and records that the reviewer opened the link. */
    public Optional<ResolvedLink> open(String rawToken) {
        Optional<ResolvedLink> resolved = resolve(rawToken);
        resolved.ifPresent(link -> {
            Instant now = clock.instant();
            links.markFirstOpened(link.linkId(), now);
            record("ReviewLinkOpened", "reviewer:" + link.reviewerId().value(), link.revisionId(),
                    JsonPayload.of("linkId", link.linkId().value().toString()), now);
        });
        return resolved;
    }

    /** The state of the reviewer's most recent link for this revision. */
    @Transactional(readOnly = true)
    public Optional<LinkStatus> statusOf(RevisionId revisionId, ReviewerId reviewerId) {
        boolean answered = reviews.find(revisionId)
                .map(review -> review.history().stream().anyMatch(o -> o.reviewerId().equals(reviewerId)))
                .orElse(false);
        return links.findByRevision(revisionId).stream()
                .filter(link -> link.reviewerId().equals(reviewerId))
                .reduce((older, newer) -> newer)
                .map(link -> link.statusAt(clock.instant(), answered));
    }

    private void record(String type, String actor, RevisionId revisionId, String payload, Instant at) {
        auditLog.append(AuditEvent.of(type, actor, TARGET_TYPE, revisionId.value(), payload, at));
    }
}
