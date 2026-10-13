package io.github.abdellahjebar.paraphe.reviews.infrastructure.persistence;

import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLink;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLinkId;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.TokenHash;
import io.github.abdellahjebar.paraphe.reviews.port.ReviewLinkRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReviewLinkRepository implements ReviewLinkRepository {

    private static final String COLUMNS = """
            id, revision_id, reviewer_id, token_hash, issued_at, expires_at, first_opened_at, revoked_at
            """;

    private final JdbcClient jdbc;

    JdbcReviewLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ReviewLink link) {
        jdbc.sql("""
                INSERT INTO review_link (id, revision_id, reviewer_id, token_hash, issued_at, expires_at)
                VALUES (:id, :revision, :reviewer, :hash, :issuedAt, :expiresAt)
                """)
                .param("id", link.id().value())
                .param("revision", link.revisionId().value())
                .param("reviewer", link.reviewerId().value())
                .param("hash", link.tokenHash().hex())
                .param("issuedAt", utc(link.issuedAt()))
                .param("expiresAt", utc(link.expiresAt()))
                .update();
    }

    @Override
    public Optional<ReviewLink> findByTokenHash(TokenHash hash) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM review_link WHERE token_hash = :hash")
                .param("hash", hash.hex())
                .query((rs, n) -> toLink(rs))
                .optional();
    }

    @Override
    public Optional<ReviewLink> findActive(RevisionId revisionId, ReviewerId reviewerId) {
        return jdbc.sql("""
                SELECT %s FROM review_link
                WHERE revision_id = :revision AND reviewer_id = :reviewer AND revoked_at IS NULL
                """.formatted(COLUMNS))
                .param("revision", revisionId.value())
                .param("reviewer", reviewerId.value())
                .query((rs, n) -> toLink(rs))
                .optional();
    }

    @Override
    public List<ReviewLink> findByRevision(RevisionId revisionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM review_link WHERE revision_id = :revision ORDER BY issued_at, id")
                .param("revision", revisionId.value())
                .query((rs, n) -> toLink(rs))
                .list();
    }

    @Override
    public int revokeActive(RevisionId revisionId, ReviewerId reviewerId, Instant at) {
        return jdbc.sql("""
                UPDATE review_link SET revoked_at = :at
                WHERE revision_id = :revision AND reviewer_id = :reviewer AND revoked_at IS NULL
                """)
                .param("at", utc(at))
                .param("revision", revisionId.value())
                .param("reviewer", reviewerId.value())
                .update();
    }

    @Override
    public void markFirstOpened(ReviewLinkId id, Instant at) {
        jdbc.sql("UPDATE review_link SET first_opened_at = :at WHERE id = :id AND first_opened_at IS NULL")
                .param("at", utc(at))
                .param("id", id.value())
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static ReviewLink toLink(ResultSet rs) throws SQLException {
        return new ReviewLink(
                new ReviewLinkId(rs.getObject("id", UUID.class)),
                new RevisionId(rs.getObject("revision_id", UUID.class)),
                new ReviewerId(rs.getObject("reviewer_id", UUID.class)),
                new TokenHash(rs.getString("token_hash")),
                instantOrNull(rs, "issued_at"),
                instantOrNull(rs, "expires_at"),
                instantOrNull(rs, "first_opened_at"),
                instantOrNull(rs, "revoked_at"));
    }
}
