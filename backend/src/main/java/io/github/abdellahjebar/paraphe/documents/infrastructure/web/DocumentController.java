package io.github.abdellahjebar.paraphe.documents.infrastructure.web;

import io.github.abdellahjebar.paraphe.documents.application.DocumentService;
import io.github.abdellahjebar.paraphe.documents.application.RevisionFile;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.shared.security.AuthenticatedUser;
import io.github.abdellahjebar.paraphe.shared.web.PdfResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Documents of a project and their revisions. Depositing a revision (which also sends it for
 * review) is in the submission module, because it spans several modules.
 *
 * <p>Every identifier is checked against the project in the URL: a document or revision of another
 * project is "not found", whatever its identifier.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
class DocumentController {

    private static final String GUARD = "@projectAccessGuard.isMember(#projectId, authentication)";

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    record CreateDocumentRequest(
            @NotBlank @Size(max = Document.MAX_TITLE_LENGTH) String title,
            @Size(max = 50) String workPackage,
            @Size(max = Document.MAX_TYPE_LENGTH) String type) {
    }

    record RevisionView(UUID id, String label, int sequence, Instant submittedAt, String fileName, long sizeBytes,
                        String sha256, String externalLink, boolean superseded) {
    }

    record DocumentSummary(UUID id, String title, String workPackage, String type, Instant createdAt,
                           int revisionCount, RevisionView latestRevision) {
    }

    record DocumentDetail(UUID id, String title, String workPackage, String type, Instant createdAt,
                          List<RevisionView> revisions) {
    }

    @GetMapping("/documents")
    @PreAuthorize(GUARD)
    List<DocumentSummary> list(@PathVariable UUID projectId) {
        return documents.documentsOf(new ProjectId(projectId)).stream().map(document -> {
            List<RevisionView> revisions = revisionViews(documents.revisions(document.id()));
            Optional<RevisionView> latest = revisions.isEmpty() ? Optional.empty()
                    : Optional.of(revisions.get(revisions.size() - 1));
            return new DocumentSummary(document.id().value(), document.title(), document.workPackageCode(),
                    document.type(), document.createdAt(), revisions.size(), latest.orElse(null));
        }).toList();
    }

    @PostMapping("/documents")
    @PreAuthorize(GUARD)
    ResponseEntity<DocumentSummary> create(@PathVariable UUID projectId,
                                           @Valid @RequestBody CreateDocumentRequest body,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        Document document = documents.createDocument(new ProjectId(projectId), body.title(), body.workPackage(),
                body.type(), "user:" + user.userId());
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/documents/" + document.id().value()))
                .body(new DocumentSummary(document.id().value(), document.title(), document.workPackageCode(),
                        document.type(), document.createdAt(), 0, null));
    }

    @GetMapping("/documents/{documentId}")
    @PreAuthorize(GUARD)
    DocumentDetail detail(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        Document document = documents.documentInProject(new ProjectId(projectId), new DocumentId(documentId));
        return new DocumentDetail(document.id().value(), document.title(), document.workPackageCode(),
                document.type(), document.createdAt(), revisionViews(documents.revisions(document.id())));
    }

    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/pdf",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
    @GetMapping("/revisions/{revisionId}/file")
    @PreAuthorize(GUARD)
    ResponseEntity<InputStreamResource> file(@PathVariable UUID projectId, @PathVariable UUID revisionId) {
        ProjectId project = new ProjectId(projectId);
        documents.revisionInProject(project, new RevisionId(revisionId));
        RevisionFile file = documents.open(new RevisionId(revisionId));
        return PdfResponses.inline(file.content(), file.revision().originalFileName(),
                file.revision().file().sizeBytes());
    }

    private static List<RevisionView> revisionViews(List<Revision> revisions) {
        int newest = revisions.stream().mapToInt(Revision::sequence).max().orElse(0);
        return revisions.stream().map(r -> new RevisionView(r.id().value(), r.label(), r.sequence(),
                r.submittedAt(), r.originalFileName(), r.file().sizeBytes(), r.file().sha256(),
                r.externalLink(), r.sequence() < newest)).toList();
    }
}
