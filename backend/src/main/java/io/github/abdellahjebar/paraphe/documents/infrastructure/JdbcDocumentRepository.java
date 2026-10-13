package io.github.abdellahjebar.paraphe.documents.infrastructure;

import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.documents.domain.StoredFile;
import io.github.abdellahjebar.paraphe.documents.port.DocumentRepository;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDocumentRepository implements DocumentRepository {

    private static final String REVISION_COLUMNS = """
            id, document_id, sequence, label, submitted_at,
            file_key, file_name, file_size, file_sha256, external_link
            """;

    private final JdbcClient jdbc;

    JdbcDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertDocument(Document document) {
        jdbc.sql("""
                INSERT INTO document (id, project_id, work_package_code, title, document_type, created_at)
                VALUES (:id, :project, :workPackage, :title, :type, :createdAt)
                """)
                .param("id", document.id().value())
                .param("project", document.projectId().value())
                .param("workPackage", document.workPackageCode())
                .param("title", document.title())
                .param("type", document.type())
                .param("createdAt", OffsetDateTime.ofInstant(document.createdAt(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public Optional<Document> findDocument(DocumentId id) {
        return jdbc.sql("""
                SELECT id, project_id, work_package_code, title, document_type, created_at
                FROM document WHERE id = :id
                """)
                .param("id", id.value())
                .query((rs, n) -> new Document(
                        new DocumentId(rs.getObject("id", UUID.class)),
                        new ProjectId(rs.getObject("project_id", UUID.class)),
                        rs.getString("work_package_code"),
                        rs.getString("title"),
                        rs.getString("document_type"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public void insertRevision(Revision revision) {
        jdbc.sql("""
                INSERT INTO revision (id, document_id, sequence, label, submitted_at,
                                      file_key, file_name, file_size, file_sha256, external_link)
                VALUES (:id, :document, :sequence, :label, :submittedAt,
                        :fileKey, :fileName, :fileSize, :fileSha256, :link)
                """)
                .param("id", revision.id().value())
                .param("document", revision.documentId().value())
                .param("sequence", revision.sequence())
                .param("label", revision.label())
                .param("submittedAt", OffsetDateTime.ofInstant(revision.submittedAt(), ZoneOffset.UTC))
                .param("fileKey", revision.file().key())
                .param("fileName", revision.originalFileName())
                .param("fileSize", revision.file().sizeBytes())
                .param("fileSha256", revision.file().sha256())
                .param("link", revision.externalLink())
                .update();
    }

    @Override
    public Optional<Revision> findRevision(RevisionId id) {
        return jdbc.sql("SELECT " + REVISION_COLUMNS + " FROM revision WHERE id = :id")
                .param("id", id.value())
                .query((rs, n) -> toRevision(rs))
                .optional();
    }

    @Override
    public List<Revision> revisions(DocumentId documentId) {
        return jdbc.sql("SELECT " + REVISION_COLUMNS + " FROM revision WHERE document_id = :d ORDER BY sequence")
                .param("d", documentId.value())
                .query((rs, n) -> toRevision(rs))
                .list();
    }

    @Override
    public int nextSequence(DocumentId documentId) {
        return jdbc.sql("SELECT COALESCE(MAX(sequence), 0) + 1 FROM revision WHERE document_id = :d")
                .param("d", documentId.value())
                .query(Integer.class)
                .single();
    }

    @Override
    public boolean labelExists(DocumentId documentId, String label) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM revision WHERE document_id = :d AND lower(label) = lower(:label))
                """)
                .param("d", documentId.value())
                .param("label", label)
                .query(Boolean.class)
                .single();
    }

    private static Revision toRevision(ResultSet rs) throws SQLException {
        return new Revision(
                new RevisionId(rs.getObject("id", UUID.class)),
                new DocumentId(rs.getObject("document_id", UUID.class)),
                rs.getInt("sequence"),
                rs.getString("label"),
                rs.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                new StoredFile(rs.getString("file_key"), rs.getLong("file_size"), rs.getString("file_sha256")),
                rs.getString("file_name"),
                rs.getString("external_link"));
    }
}
