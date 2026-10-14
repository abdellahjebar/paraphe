package io.github.abdellahjebar.paraphe.submission.infrastructure;

import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.submission.port.DashboardReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Four fixed queries per dashboard, whatever the number of documents. Explicit SQL on purpose: this
 * is a read model, and the "newest revision of each document" shape is awkward for an ORM.
 */
@Repository
class JdbcDashboardReader implements DashboardReader {

    /** Newest revision id of every document of the project. */
    private static final String LATEST = """
            WITH latest AS (
                SELECT DISTINCT ON (r.document_id) r.id
                FROM revision r JOIN document d ON d.id = r.document_id
                WHERE d.project_id = :p
                ORDER BY r.document_id, r.sequence DESC
            )
            """;

    private final JdbcClient jdbc;

    JdbcDashboardReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DocumentRow> documents(ProjectId projectId) {
        return jdbc.sql("""
                SELECT d.id AS document_id, d.title, d.work_package_code, d.document_type,
                       (SELECT count(*) FROM revision c WHERE c.document_id = d.id) AS revision_count,
                       r.id AS revision_id, r.label, r.sequence, r.submitted_at
                FROM document d
                LEFT JOIN LATERAL (
                    SELECT id, label, sequence, submitted_at FROM revision
                    WHERE document_id = d.id ORDER BY sequence DESC LIMIT 1
                ) r ON TRUE
                WHERE d.project_id = :p
                ORDER BY d.created_at, d.id
                """)
                .param("p", projectId.value())
                .query((rs, n) -> {
                    UUID revisionId = rs.getObject("revision_id", UUID.class);
                    Optional<LatestRevision> latest = revisionId == null ? Optional.empty()
                            : Optional.of(new LatestRevision(new RevisionId(revisionId), rs.getString("label"),
                                    rs.getInt("sequence"),
                                    rs.getObject("submitted_at", OffsetDateTime.class).toInstant()));
                    return new DocumentRow(rs.getObject("document_id", UUID.class), rs.getString("title"),
                            rs.getString("work_package_code"), rs.getString("document_type"),
                            rs.getInt("revision_count"), latest);
                })
                .list();
    }

    @Override
    public Map<RevisionId, Set<ReviewerId>> expectedReviewers(ProjectId projectId) {
        Map<RevisionId, Set<ReviewerId>> expected = new HashMap<>();
        jdbc.sql(LATEST + """
                SELECT rr.revision_id, rr.reviewer_id
                FROM review_request rr JOIN latest l ON l.id = rr.revision_id
                ORDER BY rr.revision_id, rr.reviewer_id
                """)
                .param("p", projectId.value())
                .query((rs, n) -> {
                    expected.computeIfAbsent(new RevisionId(rs.getObject("revision_id", UUID.class)),
                            k -> new LinkedHashSet<>())
                            .add(new ReviewerId(rs.getObject("reviewer_id", UUID.class)));
                    return null;
                })
                .list();
        return expected;
    }

    @Override
    public Map<RevisionId, List<Opinion>> opinions(ProjectId projectId) {
        Map<RevisionId, List<Opinion>> opinions = new HashMap<>();
        jdbc.sql(LATEST + """
                SELECT o.revision_id, o.reviewer_id, o.status_code, o.status_label, o.effect,
                       o.comment, o.given_at, o.entered_by
                FROM opinion o JOIN latest l ON l.id = o.revision_id
                ORDER BY o.seq
                """)
                .param("p", projectId.value())
                .query((rs, n) -> {
                    opinions.computeIfAbsent(new RevisionId(rs.getObject("revision_id", UUID.class)),
                            k -> new ArrayList<>()).add(toOpinion(rs));
                    return null;
                })
                .list();
        return opinions;
    }

    @Override
    public Map<RevisionId, Map<ReviewerId, LinkFacts>> links(ProjectId projectId) {
        Map<RevisionId, Map<ReviewerId, LinkFacts>> links = new HashMap<>();
        jdbc.sql(LATEST + """
                SELECT k.revision_id, k.reviewer_id, k.issued_at, k.expires_at, k.first_opened_at, k.revoked_at
                FROM review_link k JOIN latest l ON l.id = k.revision_id
                ORDER BY k.issued_at, k.id
                """)
                .param("p", projectId.value())
                .query((rs, n) -> {
                    // Oldest first, so the last one read for a reviewer is the most recent link.
                    links.computeIfAbsent(new RevisionId(rs.getObject("revision_id", UUID.class)),
                            k -> new LinkedHashMap<>())
                            .put(new ReviewerId(rs.getObject("reviewer_id", UUID.class)), new LinkFacts(
                                    instant(rs, "issued_at"), instant(rs, "expires_at"),
                                    instant(rs, "first_opened_at"), instant(rs, "revoked_at")));
                    return null;
                })
                .list();
        return links;
    }

    private static Opinion toOpinion(ResultSet rs) throws SQLException {
        return new Opinion(
                new ReviewerId(rs.getObject("reviewer_id", UUID.class)),
                new OpinionStatus(rs.getString("status_code"), rs.getString("status_label"),
                        OpinionEffect.valueOf(rs.getString("effect"))),
                rs.getString("comment"),
                instant(rs, "given_at"),
                rs.getString("entered_by"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
