package io.github.abdellahjebar.paraphe.documents.port;

import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    void insertDocument(Document document);

    Optional<Document> findDocument(DocumentId id);

    /** Documents of a project, oldest first. */
    List<Document> documentsOf(ProjectId projectId);

    void insertRevision(Revision revision);

    Optional<Revision> findRevision(RevisionId id);

    /** Revisions of a document, oldest first. */
    List<Revision> revisions(DocumentId documentId);

    /** The sequence number the next revision of this document must take. */
    int nextSequence(DocumentId documentId);

    /** Case-insensitive: "a" and "A" are the same index. */
    boolean labelExists(DocumentId documentId, String label);
}
