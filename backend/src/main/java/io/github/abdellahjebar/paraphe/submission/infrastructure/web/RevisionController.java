package io.github.abdellahjebar.paraphe.submission.infrastructure.web;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.documents.application.DocumentService;
import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.reviews.application.IssuedLink;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewLinkService;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewService;
import io.github.abdellahjebar.paraphe.reviews.domain.LinkStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionReview;
import io.github.abdellahjebar.paraphe.shared.security.AuthenticatedUser;
import io.github.abdellahjebar.paraphe.submission.application.SubmissionService;
import io.github.abdellahjebar.paraphe.submission.application.SubmitRequest;
import io.github.abdellahjebar.paraphe.submission.application.SubmittedRevision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sending a revision for review and following it: the review, its links, answers entered on
 * behalf of a reviewer, and the journal. Every identifier is checked against the project in the URL.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
class RevisionController {

    private static final String GUARD = "@projectAccessGuard.isMember(#projectId, authentication)";

    private final SubmissionService submission;
    private final DocumentService documents;
    private final ProjectService projects;
    private final ReviewService reviews;
    private final ReviewLinkService links;
    private final AuditLog auditLog;
    private final JsonMapper json;
    private final String publicBaseUrl;

    RevisionController(SubmissionService submission, DocumentService documents, ProjectService projects,
                       ReviewService reviews, ReviewLinkService links, AuditLog auditLog, JsonMapper json,
                       @Value("${paraphe.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.submission = submission;
        this.documents = documents;
        this.projects = projects;
        this.reviews = reviews;
        this.links = links;
        this.auditLog = auditLog;
        this.json = json;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    // ---- requests and responses --------------------------------------------------------------

    record OnBehalfRequest(@NotNull UUID reviewerId, @NotBlank @Size(max = 20) String statusCode,
                           @Size(max = 5000) String comment) {
    }

    record RevisionSummary(UUID id, String label, int sequence, Instant submittedAt, String fileName,
                           long sizeBytes, String sha256, String externalLink) {
        static RevisionSummary of(Revision r) {
            return new RevisionSummary(r.id().value(), r.label(), r.sequence(), r.submittedAt(),
                    r.originalFileName(), r.file().sizeBytes(), r.file().sha256(), r.externalLink());
        }
    }

    /** The link a reviewer opens. The only place a token is ever shown. */
    record LinkView(UUID reviewerId, String url, Instant expiresAt) {
    }

    record SubmittedResponse(RevisionSummary revision, List<LinkView> links) {
    }

    record OpinionLine(String statusCode, String statusLabel, OpinionEffect effect, String comment,
                       Instant givenAt, String enteredBy) {
    }

    record ReviewerLine(UUID reviewerId, String name, ReviewerRole role, String specialty, boolean answered,
                        OpinionLine lastOpinion, LinkStatus linkStatus) {
    }

    record ReviewResponse(UUID revisionId, String label, int sequence, boolean superseded, String status,
                          List<ReviewerLine> reviewers) {
    }

    record StatusResponse(String status) {
    }

    record EventView(String type, String actor, Instant occurredAt, Map<String, Object> details) {
    }

    // ---- sending a revision ------------------------------------------------------------------

    @PostMapping(path = "/documents/{documentId}/revisions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GUARD)
    ResponseEntity<SubmittedResponse> submit(
            @PathVariable UUID projectId, @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("label") String label,
            @RequestParam(value = "externalLink", required = false) String externalLink,
            @RequestParam(value = "reviewerIds", required = false) List<UUID> reviewerIds,
            @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        documents.documentInProject(new ProjectId(projectId), new DocumentId(documentId));
        Set<ReviewerId> reviewers = reviewerIds == null ? null
                : reviewerIds.stream().map(ReviewerId::new).collect(Collectors.toCollection(HashSet::new));

        SubmittedRevision submitted = submission.submitForReview(new SubmitRequest(new DocumentId(documentId),
                label, file.getInputStream(), file.getOriginalFilename(), externalLink, reviewers,
                actor(user)));

        List<LinkView> views = submitted.links().stream()
                .map(l -> linkView(l.reviewerId(), l.link())).toList();
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/revisions/" + submitted.revision().id().value()))
                .body(new SubmittedResponse(RevisionSummary.of(submitted.revision()), views));
    }

    // ---- following the review ----------------------------------------------------------------

    @GetMapping("/revisions/{revisionId}/review")
    @PreAuthorize(GUARD)
    ReviewResponse review(@PathVariable UUID projectId, @PathVariable UUID revisionId) {
        ProjectId project = new ProjectId(projectId);
        RevisionId id = new RevisionId(revisionId);
        Revision revision = documents.revisionInProject(project, id);
        RevisionReview review = reviews.review(id);
        Map<ReviewerId, Participant> participants = projects.participants(project).stream()
                .collect(Collectors.toMap(Participant::id, Function.identity()));

        Map<ReviewerId, Opinion> last = new LinkedHashMap<>();
        review.history().forEach(o -> last.put(o.reviewerId(), o));

        List<ReviewerLine> lines = review.expected().stream().map(reviewerId -> {
            Participant p = participants.get(reviewerId);
            Opinion opinion = last.get(reviewerId);
            return new ReviewerLine(reviewerId.value(), p == null ? null : p.name(), p == null ? null : p.role(),
                    p == null ? null : p.specialty(), opinion != null, opinion == null ? null : line(opinion),
                    links.statusOf(id, reviewerId).orElse(null));
        }).toList();

        boolean superseded = documents.revisions(revision.documentId()).stream()
                .anyMatch(other -> other.sequence() > revision.sequence());
        return new ReviewResponse(revision.id().value(), revision.label(), revision.sequence(), superseded,
                review.status().name(), lines);
    }

    @PostMapping("/revisions/{revisionId}/reviewers/{reviewerId}/link")
    @PreAuthorize(GUARD)
    ResponseEntity<LinkView> issueLink(@PathVariable UUID projectId, @PathVariable UUID revisionId,
                                       @PathVariable UUID reviewerId,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        documents.revisionInProject(new ProjectId(projectId), new RevisionId(revisionId));
        IssuedLink issued = links.issue(new RevisionId(revisionId), new ReviewerId(reviewerId), actor(user));
        return ResponseEntity.status(201).body(linkView(new ReviewerId(reviewerId), issued));
    }

    @PostMapping("/revisions/{revisionId}/reviewers/{reviewerId}/link/revoke")
    @PreAuthorize(GUARD)
    ResponseEntity<Void> revokeLink(@PathVariable UUID projectId, @PathVariable UUID revisionId,
                                    @PathVariable UUID reviewerId,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        documents.revisionInProject(new ProjectId(projectId), new RevisionId(revisionId));
        links.revoke(new RevisionId(revisionId), new ReviewerId(reviewerId), actor(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revisions/{revisionId}/opinions")
    @PreAuthorize(GUARD)
    StatusResponse enterOnBehalf(@PathVariable UUID projectId, @PathVariable UUID revisionId,
                                 @Valid @RequestBody OnBehalfRequest body,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        documents.revisionInProject(new ProjectId(projectId), new RevisionId(revisionId));
        var status = submission.recordOpinionOnBehalf(new RevisionId(revisionId), new ReviewerId(body.reviewerId()),
                body.statusCode(), body.comment(), actor(user));
        return new StatusResponse(status.name());
    }

    @GetMapping("/revisions/{revisionId}/history")
    @PreAuthorize(GUARD)
    List<EventView> history(@PathVariable UUID projectId, @PathVariable UUID revisionId) {
        documents.revisionInProject(new ProjectId(projectId), new RevisionId(revisionId));
        return auditLog.findByTarget("REVISION", revisionId).stream().map(this::eventView).toList();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private LinkView linkView(ReviewerId reviewerId, IssuedLink issued) {
        return new LinkView(reviewerId.value(), publicBaseUrl + issued.path(), issued.expiresAt());
    }

    private static OpinionLine line(Opinion o) {
        return new OpinionLine(o.status().code(), o.status().label(), o.status().effect(), o.comment(),
                o.givenAt(), o.enteredBy());
    }

    @SuppressWarnings("unchecked")
    private EventView eventView(AuditEvent event) {
        Map<String, Object> details = json.readValue(event.payload(), Map.class);
        return new EventView(event.type(), event.actor(), event.occurredAt(), details);
    }

    private static String actor(AuthenticatedUser user) {
        return "user:" + user.userId();
    }
}
