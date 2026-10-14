package io.github.abdellahjebar.paraphe.submission.port;

import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads everything the dashboard needs in a handful of queries, never one query per document.
 * It only reads facts: the status of a revision is derived afterwards by the domain, so the rules
 * live in exactly one place.
 */
public interface DashboardReader {

    /** One document of the project with its newest revision, if it has one. */
    record DocumentRow(UUID documentId, String title, String workPackageCode, String type,
                       int revisionCount, Optional<LatestRevision> latest) {
    }

    record LatestRevision(RevisionId id, String label, int sequence, Instant submittedAt) {
    }

    /** What happened to a reviewer's link for a revision. */
    record LinkFacts(Instant issuedAt, Instant expiresAt, Instant firstOpenedAt, Instant revokedAt) {
    }

    List<DocumentRow> documents(ProjectId projectId);

    /** Expected reviewers of the newest revision of each document. */
    Map<RevisionId, Set<ReviewerId>> expectedReviewers(ProjectId projectId);

    /** Opinions on the newest revisions, oldest first. */
    Map<RevisionId, List<Opinion>> opinions(ProjectId projectId);

    /** The most recent link per reviewer, for the newest revisions. */
    Map<RevisionId, Map<ReviewerId, LinkFacts>> links(ProjectId projectId);
}
