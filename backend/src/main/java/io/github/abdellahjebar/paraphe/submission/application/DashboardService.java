package io.github.abdellahjebar.paraphe.submission.application;

import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.domain.LinkStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionReview;
import io.github.abdellahjebar.paraphe.submission.port.DashboardReader;
import io.github.abdellahjebar.paraphe.submission.port.DashboardReader.DocumentRow;
import io.github.abdellahjebar.paraphe.submission.port.DashboardReader.LatestRevision;
import io.github.abdellahjebar.paraphe.submission.port.DashboardReader.LinkFacts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the follow-up screen. The reader supplies facts; the status of every revision comes from
 * {@link RevisionReview}, the same code that decides it everywhere else.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardReader reader;
    private final ProjectService projects;
    private final Clock clock;

    DashboardService(DashboardReader reader, ProjectService projects, Clock clock) {
        this.reader = reader;
        this.projects = projects;
        this.clock = clock;
    }

    /**
     * @param workPackage only documents of this work package, or all when null or blank
     * @param status only documents with this dashboard status (a revision status, {@code NOT_SENT} or
     *     {@code NO_REVISION}), or all when null or blank
     */
    public DashboardView dashboard(ProjectId projectId, String workPackage, String status) {
        Instant now = clock.instant();
        Map<ReviewerId, Participant> participants = projects.participants(projectId).stream()
                .collect(Collectors.toMap(Participant::id, Function.identity()));

        Map<RevisionId, Set<ReviewerId>> expected = reader.expectedReviewers(projectId);
        Map<RevisionId, List<Opinion>> opinions = reader.opinions(projectId);
        Map<RevisionId, Map<ReviewerId, LinkFacts>> links = reader.links(projectId);

        List<DashboardView.Row> rows = new ArrayList<>();
        for (DocumentRow document : reader.documents(projectId)) {
            rows.add(row(document, expected, opinions, links, participants, now));
        }

        Map<String, Integer> totals = new LinkedHashMap<>();
        rows.forEach(row -> totals.merge(row.status(), 1, Integer::sum));

        List<DashboardView.Row> filtered = rows.stream()
                .filter(row -> isBlank(workPackage) || workPackage.equals(row.workPackage()))
                .filter(row -> isBlank(status) || status.equals(row.status()))
                .toList();
        return new DashboardView(projectId.value(), now, totals, filtered);
    }

    private static DashboardView.Row row(DocumentRow document, Map<RevisionId, Set<ReviewerId>> expected,
                                         Map<RevisionId, List<Opinion>> opinions,
                                         Map<RevisionId, Map<ReviewerId, LinkFacts>> links,
                                         Map<ReviewerId, Participant> participants, Instant now) {
        if (document.latest().isEmpty()) {
            return new DashboardView.Row(document.documentId(), document.title(), document.workPackageCode(),
                    document.type(), document.revisionCount(), null, DashboardView.NO_REVISION, List.of());
        }
        LatestRevision latest = document.latest().get();
        DashboardView.LatestRevision revision = new DashboardView.LatestRevision(latest.id().value(),
                latest.label(), latest.sequence(), latest.submittedAt());

        Set<ReviewerId> expectedHere = expected.get(latest.id());
        if (expectedHere == null || expectedHere.isEmpty()) {
            return new DashboardView.Row(document.documentId(), document.title(), document.workPackageCode(),
                    document.type(), document.revisionCount(), revision, DashboardView.NOT_SENT, List.of());
        }

        RevisionReview review = new RevisionReview(latest.id(), expectedHere);
        List<Opinion> given = opinions.getOrDefault(latest.id(), List.of());
        given.forEach(review::record);
        Map<ReviewerId, Opinion> lastByReviewer = new LinkedHashMap<>();
        given.forEach(opinion -> lastByReviewer.put(opinion.reviewerId(), opinion));

        List<DashboardView.ReviewerCell> cells = new ArrayList<>();
        for (ReviewerId reviewerId : expectedHere) {
            cells.add(cell(reviewerId, participants.get(reviewerId), lastByReviewer.get(reviewerId),
                    links.getOrDefault(latest.id(), Map.of()).get(reviewerId), latest, now));
        }
        cells.sort((a, b) -> String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name())));

        return new DashboardView.Row(document.documentId(), document.title(), document.workPackageCode(),
                document.type(), document.revisionCount(), revision, review.status().name(), cells);
    }

    private static DashboardView.ReviewerCell cell(ReviewerId reviewerId, Participant participant, Opinion last,
                                                   LinkFacts link, LatestRevision revision, Instant now) {
        String name = participant == null ? null : participant.name();
        var role = participant == null ? null : participant.role();
        String specialty = participant == null ? null : participant.specialty();
        LinkStatus linkStatus = link == null ? null : linkStatus(link, last != null, now);

        if (last == null) {
            long days = Math.max(0, Duration.between(revision.submittedAt(), now).toDays());
            return new DashboardView.ReviewerCell(reviewerId.value(), name, role, specialty,
                    DashboardView.CellState.AWAITING, null, null, null, null, false, days, linkStatus);
        }
        return new DashboardView.ReviewerCell(reviewerId.value(), name, role, specialty,
                DashboardView.CellState.ANSWERED, last.status().code(), last.status().label(),
                last.status().effect(), last.givenAt(), last.isOnBehalf(), null, linkStatus);
    }

    /** Same precedence as the link itself: revoked, then expired, then what the reviewer did. */
    private static LinkStatus linkStatus(LinkFacts link, boolean answered, Instant now) {
        if (link.revokedAt() != null) {
            return LinkStatus.REVOKED;
        }
        if (!now.isBefore(link.expiresAt())) {
            return LinkStatus.EXPIRED;
        }
        if (answered) {
            return LinkStatus.ANSWERED;
        }
        return link.firstOpenedAt() != null ? LinkStatus.OPENED : LinkStatus.ISSUED;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
