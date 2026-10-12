package io.github.abdellahjebar.paraphe.reviews.port;

import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionReview;
import java.util.Optional;

/** Storage of reviews. Opinions are only ever appended, so there is no update or delete here. */
public interface ReviewRepository {

    boolean exists(RevisionId revisionId);

    /** Stores the review and who is expected to answer. */
    void create(RevisionReview review);

    void appendOpinion(RevisionId revisionId, Opinion opinion);

    /** Rebuilds the review from its expected reviewers and its opinions, in recording order. */
    Optional<RevisionReview> find(RevisionId revisionId);
}
