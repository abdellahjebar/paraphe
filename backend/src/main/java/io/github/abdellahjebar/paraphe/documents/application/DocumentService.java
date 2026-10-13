package io.github.abdellahjebar.paraphe.documents.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;
import io.github.abdellahjebar.paraphe.documents.domain.InvalidPdfException;
import io.github.abdellahjebar.paraphe.documents.domain.PdfSignature;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.documents.domain.StoredFile;
import io.github.abdellahjebar.paraphe.documents.port.DocumentRepository;
import io.github.abdellahjebar.paraphe.documents.port.FileStorage;
import io.github.abdellahjebar.paraphe.projects.application.ProjectNotFoundException;
import io.github.abdellahjebar.paraphe.projects.application.WorkPackageNotFoundException;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.port.ProjectRepository;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documents and their revisions. A submitted revision is stored, fingerprinted and journaled in one
 * go; if anything fails afterwards, the stored file is removed so nothing is left behind.
 */
@Service
@Transactional
public class DocumentService {

    static final String DEFAULT_FILE_NAME = "document.pdf";
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final ProjectRepository projects;
    private final FileStorage storage;
    private final AuditLog auditLog;
    private final Clock clock;
    private final long maxFileSizeBytes;

    DocumentService(DocumentRepository documents, ProjectRepository projects, FileStorage storage,
                    AuditLog auditLog, Clock clock,
                    @Value("${paraphe.storage.max-file-size-bytes:26214400}") long maxFileSizeBytes) {
        this.documents = documents;
        this.projects = projects;
        this.storage = storage;
        this.auditLog = auditLog;
        this.clock = clock;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public Document createDocument(ProjectId projectId, String title, String workPackageCode,
                                   String type, String actor) {
        if (projects.find(projectId).isEmpty()) {
            throw new ProjectNotFoundException(projectId);
        }
        Document document = new Document(DocumentId.random(), projectId, workPackageCode, title, type,
                clock.instant());
        if (document.workPackageCode() != null
                && !projects.hasWorkPackage(projectId, document.workPackageCode())) {
            throw new WorkPackageNotFoundException(document.workPackageCode());
        }
        documents.insertDocument(document);
        auditLog.append(AuditEvent.of("DocumentCreated", actor, "DOCUMENT", document.id().value(),
                JsonPayload.of("projectId", projectId.value().toString()), clock.instant()));
        return document;
    }

    /**
     * Stores a new revision of a document. The index label is free ("A", "REV1") but unique within
     * the document; the sequence number is assigned here and decides which revision is the latest.
     */
    public Revision submitRevision(DocumentId documentId, String label, InputStream pdf,
                                   String originalFileName, String externalLink, String actor) {
        documents.findDocument(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
        String cleanLabel = label == null ? "" : label.strip();
        if (!cleanLabel.isEmpty() && documents.labelExists(documentId, cleanLabel)) {
            throw new DuplicateRevisionLabelException(cleanLabel);
        }

        StoredFile stored = store(pdf);
        try {
            Revision revision = new Revision(RevisionId.random(), documentId,
                    documents.nextSequence(documentId), label, clock.instant(), stored,
                    sanitizeFileName(originalFileName), externalLink);
            documents.insertRevision(revision);
            auditLog.append(AuditEvent.of("RevisionSubmitted", actor, "REVISION", revision.id().value(),
                    JsonPayload.of("documentId", documentId.value().toString(),
                            "label", revision.label(),
                            "sequence", String.valueOf(revision.sequence()),
                            "sha256", stored.sha256()),
                    revision.submittedAt()));
            return revision;
        } catch (DuplicateKeyException e) {
            discard(stored);
            throw new DuplicateRevisionLabelException(cleanLabel);
        } catch (RuntimeException e) {
            discard(stored);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Revision> revisions(DocumentId documentId) {
        documents.findDocument(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
        return documents.revisions(documentId);
    }

    @Transactional(readOnly = true)
    public Revision revision(RevisionId id) {
        return documents.findRevision(id).orElseThrow(() -> new RevisionNotFoundException(id));
    }

    /** Opens the stored PDF of a revision. The caller owns, and must close, the stream. */
    @Transactional(readOnly = true)
    public RevisionFile open(RevisionId id) {
        Revision revision = revision(id);
        try {
            return new RevisionFile(revision, storage.open(revision.file().key()));
        } catch (IOException e) {
            throw new UncheckedIOException("Stored file of revision %s cannot be read".formatted(id.value()), e);
        }
    }

    private StoredFile store(InputStream pdf) {
        try {
            BufferedInputStream in = new BufferedInputStream(pdf);
            in.mark(PdfSignature.headerLength());
            byte[] header = in.readNBytes(PdfSignature.headerLength());
            in.reset();
            if (!PdfSignature.isPdf(header)) {
                throw new InvalidPdfException();
            }
            return storage.store(in, maxFileSizeBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("The file could not be stored", e);
        }
    }

    private void discard(StoredFile stored) {
        try {
            storage.delete(stored.key());
        } catch (IOException ignored) {
            // The database row is not written either; an orphan file is harmless and swept later.
        }
    }

    /** Keeps only the last path segment and drops control characters. */
    static String sanitizeFileName(String name) {
        if (name == null) {
            return DEFAULT_FILE_NAME;
        }
        String normalized = name.replace('\\', '/');
        String base = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("\\p{Cntrl}", "")
                .strip();
        if (base.isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        return base.length() > MAX_FILE_NAME_LENGTH ? base.substring(0, MAX_FILE_NAME_LENGTH) : base;
    }
}
