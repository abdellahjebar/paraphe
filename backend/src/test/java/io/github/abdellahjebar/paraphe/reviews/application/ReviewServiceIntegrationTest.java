package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.support.OwnerJdbc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.UnexpectedReviewerException;
import io.github.abdellahjebar.paraphe.reviews.port.ReviewRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReviewServiceIntegrationTest {

    private static final OpinionStatus VSO = new OpinionStatus("VSO", "Visé sans observation", OpinionEffect.APPROVING);
    private static final OpinionStatus VAO = new OpinionStatus("VAO", "Visé avec observations", OpinionEffect.APPROVING_WITH_RESERVES);
    private static final OpinionStatus NC = new OpinionStatus("NC", "Non conforme", OpinionEffect.BLOCKING);

    @Autowired
    private ReviewService service;

    @Autowired
    private ReviewRepository repository;

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private OwnerJdbc owner;

    @Autowired
    private TestFixtures fixtures;

    private Project project;
    private ReviewerId controlOffice;
    private ReviewerId engineeringOffice;
    private ReviewerId architect;

    @BeforeEach
    void setUp() {
        project = fixtures.project();
        controlOffice = fixtures.participant(project.id(), ReviewerRole.CONTROL_OFFICE).id();
        engineeringOffice = fixtures.participant(project.id(), ReviewerRole.ENGINEERING_OFFICE).id();
        architect = fixtures.participant(project.id(), ReviewerRole.ARCHITECT).id();
    }

    private RevisionId openReview(ReviewerId... expected) {
        RevisionId revision = fixtures.revision(project.id()).id();
        service.open(revision, Set.of(expected), "contractor");
        return revision;
    }

    private int countOpinions(RevisionId revision) {
        return jdbc.sql("SELECT count(*) FROM opinion WHERE revision_id = :id")
                .param("id", revision.value()).query(Integer.class).single();
    }

    @Test
    void newReviewIsPendingAndAwaitsEveryone() {
        RevisionId revision = openReview(controlOffice, engineeringOffice);

        assertThat(service.status(revision)).isEqualTo(RevisionStatus.PENDING);
        assertThat(service.awaiting(revision)).containsExactlyInAnyOrder(controlOffice, engineeringOffice);
    }

    @Test
    void statusIsDerivedFromStoredOpinionsAfterReload() {
        RevisionId revision = openReview(controlOffice, engineeringOffice);

        service.recordOpinion(revision, controlOffice, VSO, "");
        RevisionStatus afterSecond = service.recordOpinion(revision, engineeringOffice, VAO, "fournir la fiche technique");

        assertThat(afterSecond).isEqualTo(RevisionStatus.APPROVED_WITH_RESERVES);
        assertThat(service.status(revision)).isEqualTo(RevisionStatus.APPROVED_WITH_RESERVES);
        assertThat(service.awaiting(revision)).isEmpty();
    }

    @Test
    void latestOpinionCountsAndHistoryIsKeptInOrder() {
        RevisionId revision = openReview(controlOffice);

        service.recordOpinion(revision, controlOffice, NC, "plan à reprendre");
        service.recordOpinion(revision, controlOffice, VSO, "corrigé");

        assertThat(service.status(revision)).isEqualTo(RevisionStatus.APPROVED);
        assertThat(repository.find(revision).orElseThrow().history())
                .extracting(o -> o.status().code())
                .containsExactly("NC", "VSO");
    }

    @Test
    void opinionKeepsTheStatusItWasGivenWith() {
        RevisionId revision = openReview(controlOffice);
        service.recordOpinion(revision, controlOffice, VAO, "à compléter");

        var stored = repository.find(revision).orElseThrow().history().get(0);

        assertThat(stored.status()).isEqualTo(VAO);
        assertThat(stored.comment()).isEqualTo("à compléter");
    }

    @Test
    void unexpectedReviewerIsRejectedAndNothingIsStored() {
        RevisionId revision = openReview(controlOffice);

        assertThatThrownBy(() -> service.recordOpinion(revision, architect, VSO, ""))
                .isInstanceOf(UnexpectedReviewerException.class);

        assertThat(countOpinions(revision)).isZero();
        assertThat(auditLog.findByTarget("REVISION", revision.value()))
                .extracting(AuditEvent::type)
                .containsExactly("RevisionSubmitted", "ReviewOpened");
    }

    @Test
    void everyActionLeavesAnAuditEvent() {
        RevisionId revision = openReview(controlOffice);
        service.recordOpinion(revision, controlOffice, VAO, "avec réserve");

        List<AuditEvent> events = auditLog.findByTarget("REVISION", revision.value());

        assertThat(events).extracting(AuditEvent::type)
                .containsExactly("RevisionSubmitted", "ReviewOpened", "OpinionGiven");
        assertThat(events.get(1).actor()).isEqualTo("contractor");
        assertThat(events.get(2).actor()).isEqualTo("reviewer:" + controlOffice.value());
        assertThat(events.get(2).payload()).contains("VAO").contains("APPROVING_WITH_RESERVES");
    }

    @Test
    void auditPayloadDoesNotCarryTheFreeTextComment() {
        RevisionId revision = openReview(controlOffice);
        service.recordOpinion(revision, controlOffice, VAO, "texte \"libre\" confidentiel");

        assertThat(auditLog.findByTarget("REVISION", revision.value()))
                .filteredOn(event -> event.type().equals("OpinionGiven"))
                .singleElement()
                .satisfies(event -> assertThat(event.payload()).doesNotContain("confidentiel"));
    }

    @Test
    void openingTheSameRevisionTwiceIsRejected() {
        RevisionId revision = openReview(controlOffice);

        assertThatThrownBy(() -> service.open(revision, Set.of(controlOffice), "contractor"))
                .isInstanceOf(ReviewAlreadyOpenException.class);
    }

    @Test
    void unknownRevisionIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.status(RevisionId.random()))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    void databaseRefusesAnOpinionFromAReviewerThatWasNotAsked() {
        RevisionId revision = openReview(controlOffice);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO opinion (id, revision_id, reviewer_id, status_code, status_label, effect, given_at)
                VALUES (:id, :revision, :reviewer, 'VSO', 'x', 'APPROVING', now())
                """)
                .param("id", UUID.randomUUID())
                .param("revision", revision.value())
                .param("reviewer", architect.value())
                .update())
                .hasMessageContaining("foreign key");
    }

    @Test
    void reviewerWhoIsNotAParticipantAtAllIsRefusedByTheDatabase() {
        RevisionId revision = fixtures.revision(project.id()).id();

        assertThatThrownBy(() -> service.open(revision, Set.of(ReviewerId.random()), "contractor"))
                .hasMessageContaining("foreign key");
    }

    @Test
    void reviewerFromAnotherProjectIsRefusedByTheDatabase() {
        RevisionId revision = fixtures.revision(project.id()).id();
        ReviewerId stranger = fixtures.participant(fixtures.project().id(), ReviewerRole.CONTROL_OFFICE).id();

        assertThatThrownBy(() -> service.open(revision, Set.of(stranger), "contractor"))
                .hasMessageContaining("does not belong to the project");
    }

    @Test
    void reviewOfARevisionThatDoesNotExistIsRefusedByTheDatabase() {
        assertThatThrownBy(() -> service.open(RevisionId.random(), Set.of(controlOffice), "contractor"))
                .hasMessageContaining("foreign key");
    }

    @Test
    void databaseRefusesToUpdateOrDeleteAnOpinion() {
        RevisionId revision = openReview(controlOffice);
        service.recordOpinion(revision, controlOffice, VSO, "");

        assertThatThrownBy(() -> owner.client().sql("UPDATE opinion SET effect = 'BLOCKING' WHERE revision_id = :id")
                .param("id", revision.value()).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.client().sql("DELETE FROM opinion WHERE revision_id = :id")
                .param("id", revision.value()).update())
                .hasMessageContaining("append-only");

        assertThat(service.status(revision)).isEqualTo(RevisionStatus.APPROVED);
    }
}
