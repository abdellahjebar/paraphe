package io.github.abdellahjebar.paraphe.submission.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.documents.application.DocumentService;
import io.github.abdellahjebar.paraphe.documents.application.RevisionFile;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.projects.application.ParticipantNotInProjectException;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.application.ResolvedLink;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewLinkService;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewService;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionStatus;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The operations that span several modules.
 *
 * <p>Operations driven by a link return {@link Optional#empty()} when the link is not usable,
 * instead of throwing: the rejection is journaled by the link service in its own transaction, and an
 * exception here would roll that record back. Callers answer "not found" for every empty result.
 */
@Service
public class SubmissionService {

    private static final String TARGET_TYPE = "REVISION";

    private final DocumentService documents;
    private final ProjectService projects;
    private final ReviewService reviews;
    private final ReviewLinkService links;
    private final AuditLog auditLog;
    private final Clock clock;

    SubmissionService(DocumentService documents, ProjectService projects, ReviewService reviews,
                      ReviewLinkService links, AuditLog auditLog, Clock clock) {
        this.documents = documents;
        this.projects = projects;
        this.reviews = reviews;
        this.links = links;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Stores a revision, opens its review and issues one link per reviewer, as one operation: if any
     * step fails, there is no revision, no review, no link and no file.
     */
    @Transactional
    public SubmittedRevision submitForReview(SubmitRequest request) {
        Document document = documents.document(request.documentId());
        // Decided before anything is stored, so a refusal leaves nothing behind.
        Set<ReviewerId> reviewers = reviewersFor(document, request.reviewers());

        Revision revision = documents.submitRevision(request.documentId(), request.label(), request.pdf(),
                request.originalFileName(), request.externalLink(), request.actor());
        reviews.open(revision.id(), reviewers, request.actor());

        List<SubmittedRevision.ReviewerLink> issued = reviewers.stream()
                .sorted(Comparator.comparing(r -> r.value().toString()))
                .map(reviewer -> new SubmittedRevision.ReviewerLink(reviewer,
                        links.issue(revision.id(), reviewer, request.actor())))
                .toList();
        return new SubmittedRevision(revision, issued);
    }

    /** What the reviewer sees when opening their link; empty when the link is not usable. */
    public Optional<ReviewerView> openLink(String rawToken) {
        return links.open(rawToken).map(link -> {
            Revision revision = documents.revision(link.revisionId());
            Document document = documents.document(revision.documentId());
            Optional<Opinion> mine = reviews.history(link.revisionId()).stream()
                    .filter(o -> o.reviewerId().equals(link.reviewerId()))
                    .reduce((older, newer) -> newer);
            return new ReviewerView(document.title(), revision.label(), revision.sequence(),
                    isSuperseded(revision), link.expiresAt(), projects.statuses(document.projectId()), mine);
        });
    }

    /** The reviewer answers through their link. Empty when the link is not usable. */
    public Optional<OpinionRecorded> giveOpinionByLink(String rawToken, String statusCode, String comment) {
        Optional<ResolvedLink> resolved = links.resolve(rawToken);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        ResolvedLink link = resolved.get();
        Revision revision = documents.revision(link.revisionId());
        OpinionStatus status = availableStatus(documents.document(revision.documentId()).projectId(), statusCode);

        RevisionStatus result = reviews.recordOpinion(link.revisionId(), link.reviewerId(), status, comment);

        boolean superseded = isSuperseded(revision);
        if (superseded) {
            auditLog.append(AuditEvent.of("SupersededRevisionAnswered", "reviewer:" + link.reviewerId().value(),
                    TARGET_TYPE, revision.id().value(),
                    JsonPayload.of("sequence", String.valueOf(revision.sequence())), clock.instant()));
        }
        return Optional.of(new OpinionRecorded(result, superseded));
    }

    /** The reviewer reads the plan. Empty when the link is not usable. The caller closes the file. */
    public Optional<RevisionFile> viewDocumentByLink(String rawToken) {
        return links.resolve(rawToken).map(link -> {
            RevisionFile file = documents.open(link.revisionId());
            auditLog.append(AuditEvent.of("DocumentViewed", "reviewer:" + link.reviewerId().value(), TARGET_TYPE,
                    link.revisionId().value(),
                    JsonPayload.of("linkId", link.linkId().value().toString(),
                            "sha256", file.revision().file().sha256()),
                    clock.instant()));
            return file;
        });
    }

    /** The contractor records an answer received outside the application. */
    public RevisionStatus recordOpinionOnBehalf(RevisionId revisionId, ReviewerId reviewerId,
                                                String statusCode, String comment, String enteredBy) {
        Revision revision = documents.revision(revisionId);
        OpinionStatus status = availableStatus(documents.document(revision.documentId()).projectId(), statusCode);
        return reviews.recordOpinionOnBehalf(revisionId, reviewerId, status, comment, enteredBy);
    }

    private Set<ReviewerId> reviewersFor(Document document, Set<ReviewerId> requested) {
        ProjectId projectId = document.projectId();
        if (requested != null && !requested.isEmpty()) {
            Set<ReviewerId> active = projects.participants(projectId).stream()
                    .filter(Participant::active)
                    .map(Participant::id)
                    .collect(java.util.stream.Collectors.toSet());
            for (ReviewerId reviewer : requested) {
                if (!active.contains(reviewer)) {
                    throw new ParticipantNotInProjectException(reviewer);
                }
            }
            return requested;
        }
        if (document.workPackageCode() != null) {
            Set<ReviewerId> defaults = projects.expectedReviewersFor(projectId, document.workPackageCode());
            if (!defaults.isEmpty()) {
                return defaults;
            }
        }
        throw new NoReviewersException();
    }

    private OpinionStatus availableStatus(ProjectId projectId, String code) {
        String wanted = code == null ? "" : code.strip();
        return projects.statuses(projectId).stream()
                .filter(status -> status.code().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new UnknownStatusException(wanted));
    }

    private boolean isSuperseded(Revision revision) {
        return documents.revisions(revision.documentId()).stream()
                .anyMatch(other -> other.sequence() > revision.sequence());
    }
}
