package io.github.abdellahjebar.paraphe.reviews.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The review of one revision: who must answer, what they answered, and the status that follows.
 *
 * <p>Opinions are only ever appended. When a reviewer answers again, the new opinion becomes the
 * one that counts, and the previous ones stay in the history.
 */
public final class RevisionReview {

    private final RevisionId revisionId;
    private final Set<ReviewerId> expected;
    private final List<Opinion> history = new ArrayList<>();

    public RevisionReview(RevisionId revisionId, Set<ReviewerId> expected) {
        this.revisionId = Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(expected, "expected");
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("a revision needs at least one expected reviewer");
        }
        this.expected = Collections.unmodifiableSet(new LinkedHashSet<>(expected));
    }

    public RevisionId revisionId() {
        return revisionId;
    }

    public Set<ReviewerId> expected() {
        return expected;
    }

    /** Appends an opinion. Rejected when the reviewer is not one of the expected reviewers. */
    public void record(Opinion opinion) {
        Objects.requireNonNull(opinion, "opinion");
        if (!expected.contains(opinion.reviewerId())) {
            throw new UnexpectedReviewerException(revisionId, opinion.reviewerId());
        }
        history.add(opinion);
    }

    /** Every opinion ever recorded, oldest first. */
    public List<Opinion> history() {
        return Collections.unmodifiableList(history);
    }

    /** Expected reviewers who have not answered yet. Only these can be "late". */
    public Set<ReviewerId> awaiting() {
        Set<ReviewerId> awaiting = new LinkedHashSet<>(expected);
        awaiting.removeAll(latestOpinions().keySet());
        return Collections.unmodifiableSet(awaiting);
    }

    /** The status that follows from the latest opinion of each expected reviewer. */
    public RevisionStatus status() {
        Map<ReviewerId, Opinion> latest = latestOpinions();

        if (hasEffect(latest, OpinionEffect.BLOCKING)) {
            return RevisionStatus.TO_REVISE;
        }
        if (latest.size() < expected.size()) {
            return RevisionStatus.PENDING;
        }
        if (hasEffect(latest, OpinionEffect.APPROVING_WITH_RESERVES)) {
            return RevisionStatus.APPROVED_WITH_RESERVES;
        }
        if (hasEffect(latest, OpinionEffect.APPROVING)) {
            return RevisionStatus.APPROVED;
        }
        return RevisionStatus.ACKNOWLEDGED;
    }

    private Map<ReviewerId, Opinion> latestOpinions() {
        Map<ReviewerId, Opinion> latest = new LinkedHashMap<>();
        for (Opinion opinion : history) {
            latest.put(opinion.reviewerId(), opinion);
        }
        return latest;
    }

    private static boolean hasEffect(Map<ReviewerId, Opinion> latest, OpinionEffect effect) {
        return latest.values().stream().anyMatch(o -> o.status().effect() == effect);
    }
}
