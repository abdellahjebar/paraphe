package io.github.abdellahjebar.paraphe.projects.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    @Test
    void projectNameIsTrimmed() {
        assertThat(new Project(ProjectId.random(), "  Rénovation école  ", NOW).name())
                .isEqualTo("Rénovation école");
    }

    @Test
    void blankProjectNameIsRejected() {
        assertThatThrownBy(() -> new Project(ProjectId.random(), "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tooLongProjectNameIsRejected() {
        assertThatThrownBy(() -> new Project(ProjectId.random(), "x".repeat(201), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workPackageLabelFallsBackToItsCode() {
        assertThat(new WorkPackage(" Lot 01 ", null)).isEqualTo(new WorkPackage("Lot 01", "Lot 01"));
    }

    @Test
    void blankWorkPackageCodeIsRejected() {
        assertThatThrownBy(() -> new WorkPackage(" ", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void participantNeedsAtLeastOneContact() {
        assertThatThrownBy(() -> new Participant(ReviewerId.random(), ProjectId.random(), "LAK",
                ReviewerRole.ARCHITECT, " ", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email or a phone");
    }

    @Test
    void participantWithOnlyAPhoneIsAccepted() {
        Participant participant = new Participant(ReviewerId.random(), ProjectId.random(), "LAK",
                ReviewerRole.ARCHITECT, null, "+212600000000", true);

        assertThat(participant.email()).isNull();
        assertThat(participant.phone()).isEqualTo("+212600000000");
    }

    @Test
    void defaultStatusesCoverEveryEffectAndHaveUniqueCodes() {
        List<OpinionStatus> defaults = DefaultStatuses.all();

        assertThat(defaults).extracting(OpinionStatus::code)
                .containsExactlyInAnyOrder("VSO", "VAO", "VPI", "NC", "REF");
        assertThat(defaults).extracting(OpinionStatus::effect)
                .contains(OpinionEffect.APPROVING, OpinionEffect.APPROVING_WITH_RESERVES,
                        OpinionEffect.INFORMATIVE, OpinionEffect.BLOCKING);
    }
}
