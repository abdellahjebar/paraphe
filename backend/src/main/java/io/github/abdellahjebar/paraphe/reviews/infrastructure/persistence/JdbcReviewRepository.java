package io.github.abdellahjebar.paraphe.reviews.infrastructure.persistence;

import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionReview;
import io.github.abdellahjebar.paraphe.reviews.port.ReviewRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReviewRepository implements ReviewRepository {

    private final JdbcClient jdbc;

    JdbcReviewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(RevisionId revisionId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM review_request WHERE revision_id = :revisionId)")
                .param("revisionId", revisionId.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public void create(RevisionReview review) {
        for (ReviewerId reviewer : review.expected()) {
            jdbc.sql("INSERT INTO review_request (revision_id, reviewer_id) VALUES (:revisionId, :reviewerId)")
                    .param("revisionId", review.revisionId().value())
                    .param("reviewerId", reviewer.value())
                    .update();
        }
    }

    @Override
    public void appendOpinion(RevisionId revisionId, Opinion opinion) {
        jdbc.sql("""
                INSERT INTO opinion (id, revision_id, reviewer_id, status_code, status_label, effect, comment, given_at)
                VALUES (:id, :revisionId, :reviewerId, :statusCode, :statusLabel, :effect, :comment, :givenAt)
                """)
                .param("id", UUID.randomUUID())
                .param("revisionId", revisionId.value())
                .param("reviewerId", opinion.reviewerId().value())
                .param("statusCode", opinion.status().code())
                .param("statusLabel", opinion.status().label())
                .param("effect", opinion.status().effect().name())
                .param("comment", opinion.comment())
                .param("givenAt", OffsetDateTime.ofInstant(opinion.givenAt(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public Optional<RevisionReview> find(RevisionId revisionId) {
        List<UUID> expected = jdbc.sql("""
                SELECT reviewer_id FROM review_request
                WHERE revision_id = :revisionId
                ORDER BY requested_at, reviewer_id
                """)
                .param("revisionId", revisionId.value())
                .query((rs, n) -> rs.getObject("reviewer_id", UUID.class))
                .list();
        if (expected.isEmpty()) {
            return Optional.empty();
        }

        Set<ReviewerId> reviewers = new LinkedHashSet<>();
        expected.forEach(id -> reviewers.add(new ReviewerId(id)));
        RevisionReview review = new RevisionReview(revisionId, reviewers);

        List<Opinion> opinions = jdbc.sql("""
                SELECT reviewer_id, status_code, status_label, effect, comment, given_at
                FROM opinion
                WHERE revision_id = :revisionId
                ORDER BY seq
                """)
                .param("revisionId", revisionId.value())
                .query((rs, n) -> new Opinion(
                        new ReviewerId(rs.getObject("reviewer_id", UUID.class)),
                        new OpinionStatus(
                                rs.getString("status_code"),
                                rs.getString("status_label"),
                                OpinionEffect.valueOf(rs.getString("effect"))),
                        rs.getString("comment"),
                        rs.getObject("given_at", OffsetDateTime.class).toInstant()))
                .list();
        opinions.forEach(review::record);
        return Optional.of(review);
    }
}
