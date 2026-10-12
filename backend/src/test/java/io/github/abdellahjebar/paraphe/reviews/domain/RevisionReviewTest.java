package io.github.abdellahjebar.paraphe.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RevisionReviewTest {

    private static final OpinionStatus VSO = new OpinionStatus("VSO", "Visé sans observation", OpinionEffect.APPROVING);
    private static final OpinionStatus VAO = new OpinionStatus("VAO", "Visé avec observations", OpinionEffect.APPROVING_WITH_RESERVES);
    private static final OpinionStatus VPI = new OpinionStatus("VPI", "Visé pour information", OpinionEffect.INFORMATIVE);
    private static final OpinionStatus NC = new OpinionStatus("NC", "Non conforme", OpinionEffect.BLOCKING);

    private final ReviewerId controlOffice = ReviewerId.random();
    private final ReviewerId engineeringOffice = ReviewerId.random();
    private final ReviewerId architect = ReviewerId.random();

    private RevisionReview reviewFor(ReviewerId... expected) {
        return new RevisionReview(RevisionId.random(), Set.of(expected));
    }

    private Opinion opinion(ReviewerId reviewer, OpinionStatus status) {
        return new Opinion(reviewer, status, "", Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    void pendingWhenNobodyAnswered() {
        RevisionReview review = reviewFor(controlOffice, engineeringOffice);

        assertThat(review.status()).isEqualTo(RevisionStatus.PENDING);
        assertThat(review.awaiting()).containsExactlyInAnyOrder(controlOffice, engineeringOffice);
    }

    @Test
    void pendingWhenOneExpectedReviewerIsMissing() {
        RevisionReview review = reviewFor(controlOffice, engineeringOffice);
        review.record(opinion(controlOffice, VAO));

        assertThat(review.status()).isEqualTo(RevisionStatus.PENDING);
        assertThat(review.awaiting()).containsExactly(engineeringOffice);
    }

    @Test
    void blockingOpinionMakesRevisionToRevise() {
        RevisionReview review = reviewFor(controlOffice);
        review.record(opinion(controlOffice, NC));

        assertThat(review.status()).isEqualTo(RevisionStatus.TO_REVISE);
    }

    @Test
    void blockingWinsEvenWhenOthersApproveOrAreMissing() {
        RevisionReview review = reviewFor(controlOffice, engineeringOffice, architect);
        review.record(opinion(controlOffice, VSO));
        review.record(opinion(engineeringOffice, NC));

        assertThat(review.status()).isEqualTo(RevisionStatus.TO_REVISE);
    }

    @Test
    void approvedWhenAllApprove() {
        RevisionReview review = reviewFor(controlOffice, engineeringOffice);
        review.record(opinion(controlOffice, VSO));
        review.record(opinion(engineeringOffice, VSO));

        assertThat(review.status()).isEqualTo(RevisionStatus.APPROVED);
        assertThat(review.awaiting()).isEmpty();
    }

    @Test
    void approvedWithReservesWhenOneReserve() {
        RevisionReview review = reviewFor(controlOffice, engineeringOffice);
        review.record(opinion(controlOffice, VSO));
        review.record(opinion(engineeringOffice, VAO));

        assertThat(review.status()).isEqualTo(RevisionStatus.APPROVED_WITH_RESERVES);
    }

    @Test
    void acknowledgedWhenOnlyInformativeOpinions() {
        RevisionReview review = reviewFor(engineeringOffice);
        review.record(opinion(engineeringOffice, VPI));

        assertThat(review.status()).isEqualTo(RevisionStatus.ACKNOWLEDGED);
    }

    @Test
    void informativeOpinionIsIgnoredAmongApprovals() {
        RevisionReview review = reviewFor(controlOffice, architect);
        review.record(opinion(controlOffice, VSO));
        review.record(opinion(architect, VPI));

        assertThat(review.status()).isEqualTo(RevisionStatus.APPROVED);
    }

    @Test
    void reviewerNotExpectedIsNeverAwaiting() {
        RevisionReview review = reviewFor(controlOffice);

        assertThat(review.awaiting()).doesNotContain(architect);
        assertThat(review.awaiting()).containsExactly(controlOffice);
    }

    @Test
    void latestOpinionCountsButHistoryIsKept() {
        RevisionReview review = reviewFor(controlOffice);
        review.record(opinion(controlOffice, NC));
        review.record(opinion(controlOffice, VSO));

        assertThat(review.status()).isEqualTo(RevisionStatus.APPROVED);
        assertThat(review.history()).hasSize(2);
        assertThat(review.history().get(0).status()).isEqualTo(NC);
    }

    @Test
    void opinionFromUnexpectedReviewerIsRejected() {
        RevisionReview review = reviewFor(controlOffice);

        assertThatThrownBy(() -> review.record(opinion(architect, VSO)))
                .isInstanceOf(UnexpectedReviewerException.class);
        assertThat(review.history()).isEmpty();
    }

    @Test
    void revisionRequiresAtLeastOneExpectedReviewer() {
        assertThatThrownBy(() -> new RevisionReview(RevisionId.random(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historyCannotBeModifiedFromOutside() {
        RevisionReview review = reviewFor(controlOffice);

        assertThatThrownBy(() -> review.history().add(opinion(controlOffice, VSO)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
