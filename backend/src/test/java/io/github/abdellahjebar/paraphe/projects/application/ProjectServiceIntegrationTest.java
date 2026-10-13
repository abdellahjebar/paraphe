package io.github.abdellahjebar.paraphe.projects.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewService;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjectServiceIntegrationTest {

    private static final String ACTOR = "contractor";

    @Autowired
    private ProjectService service;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcClient jdbc;

    private Project newProject() {
        return service.createProject("Rénovation école " + UUID.randomUUID(), ACTOR);
    }

    private Participant addControlOffice(ProjectId project) {
        return service.addParticipant(project, "Bureau de contrôle", ReviewerRole.CONTROL_OFFICE, null,
                "controle@example.org", null, ACTOR);
    }

    @Test
    void newProjectGetsTheDefaultStatuses() {
        Project project = newProject();

        assertThat(service.statuses(project.id()))
                .extracting(OpinionStatus::code)
                .containsExactly("NC", "REF", "VAO", "VPI", "VSO");
        assertThat(service.statuses(project.id()))
                .filteredOn(s -> s.code().equals("VPI"))
                .singleElement()
                .extracting(OpinionStatus::effect)
                .isEqualTo(OpinionEffect.INFORMATIVE);
    }

    @Test
    void duplicateWorkPackageCodeIsRejected() {
        Project project = newProject();
        service.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);

        assertThatThrownBy(() -> service.addWorkPackage(project.id(), "Lot 01", "Autre", ACTOR))
                .isInstanceOf(DuplicateCodeException.class);
    }

    @Test
    void sameWorkPackageCodeIsAllowedInAnotherProject() {
        Project first = newProject();
        Project second = newProject();

        service.addWorkPackage(first.id(), "Lot 01", "Gros œuvre", ACTOR);
        service.addWorkPackage(second.id(), "Lot 01", "Gros œuvre", ACTOR);

        assertThat(service.workPackages(second.id())).hasSize(1);
    }

    @Test
    void duplicateStatusCodeIsRejected() {
        Project project = newProject();

        assertThatThrownBy(() -> service.defineStatus(project.id(), "VSO", "Autre", OpinionEffect.APPROVING, ACTOR))
                .isInstanceOf(DuplicateCodeException.class);
    }

    @Test
    void aProjectCanDefineItsOwnStatus() {
        Project project = newProject();

        service.defineStatus(project.id(), "VRF", "Visé après reprise", OpinionEffect.APPROVING_WITH_RESERVES, ACTOR);

        assertThat(service.statuses(project.id())).extracting(OpinionStatus::code).contains("VRF");
    }

    @Test
    void deactivatedStatusIsNotOfferedButPastOpinionsKeepIt() {
        Project project = newProject();
        Participant control = addControlOffice(project.id());
        RevisionId revision = fixtures.revision(project.id()).id();
        reviewService.open(revision, Set.of(control.id()), ACTOR);
        OpinionStatus vao = service.statuses(project.id()).stream()
                .filter(s -> s.code().equals("VAO")).findFirst().orElseThrow();
        reviewService.recordOpinion(revision, control.id(), vao, "à compléter");

        service.deactivateStatus(project.id(), "VAO", ACTOR);

        assertThat(service.statuses(project.id())).extracting(OpinionStatus::code).doesNotContain("VAO");
        assertThat(reviewService.status(revision).name()).isEqualTo("APPROVED_WITH_RESERVES");
    }

    @Test
    void defaultReviewersAreReturnedForTheirWorkPackage() {
        Project project = newProject();
        service.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);
        Participant control = addControlOffice(project.id());
        Participant structure = service.addParticipant(project.id(), "BET structure",
                ReviewerRole.ENGINEERING_OFFICE, "Structure", "bet@example.org", null, ACTOR);

        service.setDefaultReviewers(project.id(), "Lot 01", Set.of(control.id(), structure.id()), ACTOR);

        assertThat(service.expectedReviewersFor(project.id(), "Lot 01"))
                .containsExactlyInAnyOrder(control.id(), structure.id());
    }

    @Test
    void settingDefaultReviewersReplacesThePreviousOnes() {
        Project project = newProject();
        service.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);
        Participant control = addControlOffice(project.id());
        Participant structure = service.addParticipant(project.id(), "BET structure",
                ReviewerRole.ENGINEERING_OFFICE, "Structure", "bet@example.org", null, ACTOR);
        service.setDefaultReviewers(project.id(), "Lot 01", Set.of(control.id()), ACTOR);

        service.setDefaultReviewers(project.id(), "Lot 01", Set.of(structure.id()), ACTOR);

        assertThat(service.expectedReviewersFor(project.id(), "Lot 01")).containsExactly(structure.id());
    }

    @Test
    void workPackageWithoutDefaultsExpectsNobody() {
        Project project = newProject();
        service.addWorkPackage(project.id(), "GENERAL", "Général", ACTOR);

        assertThat(service.expectedReviewersFor(project.id(), "GENERAL")).isEmpty();
    }

    @Test
    void unknownWorkPackageIsReported() {
        Project project = newProject();

        assertThatThrownBy(() -> service.expectedReviewersFor(project.id(), "Lot 99"))
                .isInstanceOf(WorkPackageNotFoundException.class);
    }

    @Test
    void defaultReviewerMustBelongToTheSameProject() {
        Project mine = newProject();
        Project other = newProject();
        service.addWorkPackage(mine.id(), "Lot 01", "Gros œuvre", ACTOR);
        Participant stranger = addControlOffice(other.id());

        assertThatThrownBy(() -> service.setDefaultReviewers(mine.id(), "Lot 01", Set.of(stranger.id()), ACTOR))
                .isInstanceOf(ParticipantNotInProjectException.class);
    }

    @Test
    void databaseItselfRefusesADefaultReviewerFromAnotherProject() {
        Project mine = newProject();
        Project other = newProject();
        service.addWorkPackage(mine.id(), "Lot 01", "Gros œuvre", ACTOR);
        Participant stranger = addControlOffice(other.id());

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO default_reviewer (project_id, work_package_code, participant_id)
                VALUES (:p, 'Lot 01', :participant)
                """)
                .param("p", mine.id().value())
                .param("participant", stranger.id().value())
                .update())
                .hasMessageContaining("foreign key");
    }

    @Test
    void inactiveParticipantCannotBecomeADefaultReviewer() {
        Project project = newProject();
        service.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);
        Participant control = addControlOffice(project.id());
        service.deactivateParticipant(project.id(), control.id(), ACTOR);

        assertThatThrownBy(() -> service.setDefaultReviewers(project.id(), "Lot 01", Set.of(control.id()), ACTOR))
                .isInstanceOf(ParticipantNotInProjectException.class);
    }

    @Test
    void participantDeactivatedLaterDisappearsFromTheExpectedList() {
        Project project = newProject();
        service.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);
        Participant control = addControlOffice(project.id());
        service.setDefaultReviewers(project.id(), "Lot 01", Set.of(control.id()), ACTOR);

        service.deactivateParticipant(project.id(), control.id(), ACTOR);

        assertThat(service.expectedReviewersFor(project.id(), "Lot 01")).isEmpty();
    }

    @Test
    void databaseRefusesAParticipantWithoutAnyContact() {
        Project project = newProject();

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO participant (id, project_id, name, role)
                VALUES (:id, :p, 'Sans contact', 'ARCHITECT')
                """)
                .param("id", UUID.randomUUID())
                .param("p", project.id().value())
                .update())
                .hasMessageContaining("check constraint");
    }

    @Test
    void unknownProjectIsReported() {
        assertThatThrownBy(() -> service.statuses(ProjectId.random()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void everyConfigurationChangeLeavesAnAuditEvent() {
        Project project = newProject();
        service.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);
        Participant control = addControlOffice(project.id());
        service.setDefaultReviewers(project.id(), "Lot 01", Set.of(control.id()), ACTOR);

        assertThat(auditLog.findByTarget("PROJECT", project.id().value()))
                .extracting(AuditEvent::type)
                .containsExactly("ProjectCreated", "WorkPackageAdded", "ParticipantAdded", "DefaultReviewersSet");
    }

    @Test
    void participantsOfAProjectAreListedWithTheirRole() {
        Project project = newProject();
        addControlOffice(project.id());
        service.addParticipant(project.id(), "Architecte", ReviewerRole.ARCHITECT, null, null,
                "+212600000000", ACTOR);

        assertThat(service.participants(project.id()))
                .extracting(Participant::role)
                .containsExactlyInAnyOrder(ReviewerRole.CONTROL_OFFICE, ReviewerRole.ARCHITECT);
    }

    @Test
    void specialtyTellsTwoEngineeringOfficesApart() {
        Project project = newProject();
        service.addParticipant(project.id(), "Bureau A", ReviewerRole.ENGINEERING_OFFICE, "Structure",
                "a@example.org", null, ACTOR);
        service.addParticipant(project.id(), "Bureau B", ReviewerRole.ENGINEERING_OFFICE, "Technique",
                "b@example.org", null, ACTOR);

        assertThat(service.participants(project.id()))
                .extracting(Participant::specialty)
                .containsExactlyInAnyOrder("Structure", "Technique");
    }

    @Test
    void otherRoleIsStoredAndReadBack() {
        Project project = newProject();
        Participant lab = service.addParticipant(project.id(), "Laboratoire", ReviewerRole.OTHER,
                "Laboratoire", "labo@example.org", null, ACTOR);

        assertThat(service.participants(project.id()))
                .filteredOn(p -> p.id().equals(lab.id()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.role()).isEqualTo(ReviewerRole.OTHER);
                    assertThat(p.specialty()).isEqualTo("Laboratoire");
                });
    }

    @Test
    void databaseRefusesAnUnknownRole() {
        Project project = newProject();

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO participant (id, project_id, name, role, email)
                VALUES (:id, :p, 'Inconnu', 'OWNER', 'x@example.org')
                """)
                .param("id", UUID.randomUUID())
                .param("p", project.id().value())
                .update())
                .hasMessageContaining("check constraint");
    }
}
