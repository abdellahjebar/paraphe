package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionReview;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionStatus;
import io.github.abdellahjebar.paraphe.reviews.port.ReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the review of a revision. Every change and its audit event are written in the same
 * transaction: either both exist or neither does.
 */
@Service
@Transactional
public class ReviewService {

    static final String TARGET_TYPE = "REVISION";

    private final ReviewRepository reviews;
    private final AuditLog auditLog;
    private final Clock clock;

    ReviewService(ReviewRepository reviews, AuditLog auditLog, Clock clock) {
        this.reviews = reviews;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** Sends a revision for review to the given reviewers. */
    public void open(RevisionId revisionId, Set<ReviewerId> expected, String actor) {
        if (reviews.exists(revisionId)) {
            throw new ReviewAlreadyOpenException(revisionId);
        }
        RevisionReview review = new RevisionReview(revisionId, expected);
        reviews.create(review);

        String reviewers = expected.stream()
                .map(r -> r.value().toString())
                .sorted()
                .collect(Collectors.joining(","));
        auditLog.append(AuditEvent.of("ReviewOpened", actor, TARGET_TYPE, revisionId.value(),
                JsonPayload.of("expectedReviewers", reviewers), now()));
    }

    /** Records a reviewer's opinion and returns the status the revision has afterwards. */
    public RevisionStatus recordOpinion(RevisionId revisionId, ReviewerId reviewerId,
                                        OpinionStatus status, String comment) {
        Opinion opinion = new Opinion(reviewerId, status, comment, now());
        return append(revisionId, opinion, "reviewer:" + reviewerId.value(),
                JsonPayload.of("statusCode", status.code(), "effect", status.effect().name()));
    }

    /**
     * Records an opinion the contractor typed for a reviewer (an answer received by phone or message).
     * The opinion and its journal event both say who entered it, so the proof never suggests the
     * reviewer used their own link.
     */
    public RevisionStatus recordOpinionOnBehalf(RevisionId revisionId, ReviewerId reviewerId,
                                                OpinionStatus status, String comment, String enteredBy) {
        if (enteredBy == null || enteredBy.isBlank()) {
            throw new IllegalArgumentException("who entered the opinion must be known");
        }
        Opinion opinion = new Opinion(reviewerId, status, comment, now(), enteredBy);
        return append(revisionId, opinion, enteredBy.strip(),
                JsonPayload.of("statusCode", status.code(), "effect", status.effect().name(),
                        "onBehalfOf", reviewerId.value().toString()));
    }

    private RevisionStatus append(RevisionId revisionId, Opinion opinion, String actor, String payload) {
        RevisionReview review = load(revisionId);
        review.record(opinion); // rejects a reviewer who was not asked
        reviews.appendOpinion(revisionId, opinion);
        auditLog.append(AuditEvent.of("OpinionGiven", actor, TARGET_TYPE, revisionId.value(), payload,
                opinion.givenAt()));
        return review.status();
    }

    /** Every opinion given on a revision, oldest first. */
    @Transactional(readOnly = true)
    public java.util.List<Opinion> history(RevisionId revisionId) {
        return load(revisionId).history();
    }

    @Transactional(readOnly = true)
    public RevisionStatus status(RevisionId revisionId) {
        return load(revisionId).status();
    }

    @Transactional(readOnly = true)
    public Set<ReviewerId> awaiting(RevisionId revisionId) {
        return load(revisionId).awaiting();
    }

    private RevisionReview load(RevisionId revisionId) {
        return reviews.find(revisionId).orElseThrow(() -> new ReviewNotFoundException(revisionId));
    }

    private Instant now() {
        return clock.instant();
    }
}
