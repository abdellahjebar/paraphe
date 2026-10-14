package io.github.abdellahjebar.paraphe.submission.application;

import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.reviews.domain.LinkStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The follow-up screen: one row per document, one cell per expected reviewer. */
public record DashboardView(UUID projectId, Instant generatedAt, Map<String, Integer> totals, List<Row> documents) {

    /** Dashboard-level states that are not revision statuses. */
    public static final String NO_REVISION = "NO_REVISION";
    public static final String NOT_SENT = "NOT_SENT";

    public record Row(UUID documentId, String title, String workPackage, String type, int revisionCount,
                      LatestRevision revision, String status, List<ReviewerCell> reviewers) {
    }

    public record LatestRevision(UUID id, String label, int sequence, Instant submittedAt) {
    }

    public enum CellState {
        ANSWERED,
        AWAITING
    }

    /**
     * @param waitingDays whole days since the revision was sent, only for a reviewer who has not
     *     answered
     */
    public record ReviewerCell(UUID reviewerId, String name, ReviewerRole role, String specialty, CellState state,
                               String statusCode, String statusLabel, OpinionEffect effect, Instant answeredAt,
                               boolean enteredOnBehalf, Long waitingDays, LinkStatus linkStatus) {
    }
}
