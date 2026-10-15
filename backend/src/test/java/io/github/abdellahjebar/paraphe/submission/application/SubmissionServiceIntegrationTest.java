package io.github.abdellahjebar.paraphe.submission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.documents.application.DocumentService;
import io.github.abdellahjebar.paraphe.documents.application.RevisionFile;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.projects.application.ParticipantNotInProjectException;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.reviews.application.IssuedLink;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewLinkService;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewService;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.UnexpectedReviewerException;
import io.github.abdellahjebar.paraphe.support.MutableClock;
import io.github.abdellahjebar.paraphe.support.TestClockConfiguration;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class SubmissionServiceIntegrationTest {

    private static final String ACTOR = "contractor";

    @Autowired
    private SubmissionService submission;

    @Autowired
    private ProjectService projects;

    @Autowired
    private DocumentService documents;

    @Autowired
    private ReviewService reviews;

    @Autowired
    private ReviewLinkService links;

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private MutableClock clock;

    private Project project;
    private ReviewerId control;
    private ReviewerId structure;
    private Document document;

    @BeforeEach
    void setUp() {
        clock.reset();
        project = fixtures.project();
        control = fixtures.participant(project.id(), ReviewerRole.CONTROL_OFFICE).id();
        structure = fixtures.participant(project.id(), ReviewerRole.ENGINEERING_OFFICE).id();
        projects.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);
        projects.setDefaultReviewers(project.id(), "Lot 01", Set.of(control, structure), ACTOR);
        document = documents.createDocument(project.id(), "Plan de poutres", "Lot 01", "PLAN", ACTOR);
    }

    private SubmitRequest request(String label, Set<ReviewerId> reviewers) {
        return new SubmitRequest(document.id(), label, new ByteArrayInputStream(TestFixtures.PDF),
                "plan.pdf", null, reviewers, ACTOR);
    }

    private List<String> eventTypes(RevisionId revision) {
        return auditLog.findByTarget("REVISION", revision.value()).stream().map(AuditEvent::type).toList();
    }

    private String tokenOf(SubmittedRevision submitted, ReviewerId reviewer) {
        return submitted.links().stream()
                .filter(l -> l.reviewerId().equals(reviewer))
                .findFirst().orElseThrow().link().token().value();
    }

    @Test
    void submittingStoresTheRevisionOpensTheReviewAndIssuesOneLinkPerReviewer() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control, structure)));

        assertThat(submitted.revision().label()).isEqualTo("A");
        assertThat(submitted.links()).extracting(SubmittedRevision.ReviewerLink::reviewerId)
                .containsExactlyInAnyOrder(control, structure);
        assertThat(reviews.status(submitted.revision().id())).isEqualTo(RevisionStatus.PENDING);
        assertThat(reviews.awaiting(submitted.revision().id())).containsExactlyInAnyOrder(control, structure);
        assertThat(links.resolve(tokenOf(submitted, control))).isPresent();
        assertThat(eventTypes(submitted.revision().id()))
                .contains("RevisionSubmitted", "ReviewOpened", "ReviewLinkIssued");
    }

    @Test
    void withoutExplicitReviewersTheWorkPackageDefaultsAreUsed() {
        SubmittedRevision submitted = submission.submitForReview(request("A", null));

        assertThat(submitted.links()).extracting(SubmittedRevision.ReviewerLink::reviewerId)
                .containsExactlyInAnyOrder(control, structure);
    }

    @Test
    void explicitReviewersOverrideTheDefaults() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));

        assertThat(submitted.links()).extracting(SubmittedRevision.ReviewerLink::reviewerId)
                .containsExactly(control);
        assertThat(reviews.awaiting(submitted.revision().id())).containsExactly(control);
    }

    @Test
    void sendingToNobodyIsRefusedAndNothingIsStored() {
        Document noDefaults = documents.createDocument(project.id(), "Sans lot", null, null, ACTOR);

        assertThatThrownBy(() -> submission.submitForReview(new SubmitRequest(noDefaults.id(), "A",
                new ByteArrayInputStream(TestFixtures.PDF), "plan.pdf", null, null, ACTOR)))
                .isInstanceOf(NoReviewersException.class);

        assertThat(documents.revisions(noDefaults.id())).isEmpty();
    }

    @Test
    void aReviewerOfAnotherProjectIsRefused() {
        ReviewerId stranger = fixtures.participant(fixtures.project().id(), ReviewerRole.CONTROL_OFFICE).id();

        assertThatThrownBy(() -> submission.submitForReview(request("A", Set.of(stranger))))
                .isInstanceOf(ParticipantNotInProjectException.class);
        assertThat(documents.revisions(document.id())).isEmpty();
    }

    @Test
    void reviewerAnswersThroughTheirLinkAndTheStatusFollows() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control, structure)));

        var first = submission.giveOpinionByLink(tokenOf(submitted, control), "VSO", "ok").orElseThrow();
        var second = submission.giveOpinionByLink(tokenOf(submitted, structure), "VAO", "fiche à fournir").orElseThrow();

        assertThat(first.status()).isEqualTo(RevisionStatus.PENDING);
        assertThat(second.status()).isEqualTo(RevisionStatus.APPROVED_WITH_RESERVES);
        assertThat(first.revisionSuperseded()).isFalse();
    }

    @Test
    void answerGivenThroughALinkIsNotMarkedAsEnteredByAnyoneElse() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));
        submission.giveOpinionByLink(tokenOf(submitted, control), "VSO", "");

        Opinion opinion = reviews.history(submitted.revision().id()).get(0);

        assertThat(opinion.isOnBehalf()).isFalse();
        assertThat(opinion.reviewerId()).isEqualTo(control);
    }

    @Test
    void unknownStatusCodeIsRejected() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));

        assertThatThrownBy(() -> submission.giveOpinionByLink(tokenOf(submitted, control), "XXX", ""))
                .isInstanceOf(UnknownStatusException.class);
        assertThat(reviews.history(submitted.revision().id())).isEmpty();
    }

    @Test
    void deactivatedStatusCanNoLongerBeGiven() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));
        projects.deactivateStatus(project.id(), "VAO", ACTOR);

        assertThatThrownBy(() -> submission.giveOpinionByLink(tokenOf(submitted, control), "VAO", ""))
                .isInstanceOf(UnknownStatusException.class);
    }

    @Test
    void invalidTokenIsRefusedWithoutAnyEffect() {
        assertThat(submission.giveOpinionByLink("x".repeat(43), "VSO", "")).isEmpty();
        assertThat(submission.openLink("not-a-token")).isEmpty();
        assertThat(submission.viewDocumentByLink(null)).isEmpty();
    }

    @Test
    void expiredLinkIsRefusedAndTheRejectionSurvivesInTheJournal() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));
        clock.advance(Duration.ofDays(31));

        assertThat(submission.giveOpinionByLink(tokenOf(submitted, control), "VSO", "")).isEmpty();

        assertThat(eventTypes(submitted.revision().id())).contains("ReviewLinkRejected");
        assertThat(reviews.history(submitted.revision().id())).isEmpty();
    }

    @Test
    void revokedLinkIsRefused() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));
        links.revoke(submitted.revision().id(), control, ACTOR);

        assertThat(submission.viewDocumentByLink(tokenOf(submitted, control))).isEmpty();
        assertThat(submission.giveOpinionByLink(tokenOf(submitted, control), "VSO", "")).isEmpty();
    }

    @Test
    void anAnswerOnAnOlderRevisionIsKeptButFlaggedAsSuperseded() {
        SubmittedRevision first = submission.submitForReview(request("A", Set.of(control)));
        submission.submitForReview(request("B", Set.of(control)));

        var outcome = submission.giveOpinionByLink(tokenOf(first, control), "VSO", "").orElseThrow();

        assertThat(outcome.revisionSuperseded()).isTrue();
        assertThat(reviews.history(first.revision().id())).hasSize(1);
        assertThat(eventTypes(first.revision().id())).contains("SupersededRevisionAnswered");
    }

    @Test
    void viewingTheDocumentReturnsTheFileAndIsJournaledWithItsFingerprint() throws Exception {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));

        try (RevisionFile file = submission.viewDocumentByLink(tokenOf(submitted, control)).orElseThrow()) {
            assertThat(file.content().readAllBytes()).isEqualTo(TestFixtures.PDF);
        }

        assertThat(auditLog.findByTarget("REVISION", submitted.revision().id().value()))
                .filteredOn(e -> e.type().equals("DocumentViewed"))
                .singleElement()
                .satisfies(e -> assertThat(e.payload()).contains(submitted.revision().file().sha256()));
    }

    @Test
    void openingTheLinkShowsTheReviewerWhatTheyNeedAndNothingMore() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control, structure)));
        projects.deactivateStatus(project.id(), "REF", ACTOR);

        ReviewerView view = submission.openLink(tokenOf(submitted, control)).orElseThrow();

        assertThat(view.documentTitle()).isEqualTo("Plan de poutres");
        assertThat(view.revisionLabel()).isEqualTo("A");
        assertThat(view.availableStatuses()).extracting(s -> s.code())
                .containsExactlyInAnyOrder("VSO", "VAO", "VPI", "NC");
        assertThat(view.myLastOpinion()).isEmpty();
        // Reading the page has no effect; only an explicit confirmation records the opening.
        assertThat(eventTypes(submitted.revision().id())).doesNotContain("ReviewLinkOpened");
        submission.confirmLinkSeen(tokenOf(submitted, control)).orElseThrow();
        assertThat(eventTypes(submitted.revision().id())).contains("ReviewLinkOpened");
    }

    @Test
    void openingTheLinkAfterAnsweringShowsTheReviewersOwnLastOpinion() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control, structure)));
        submission.giveOpinionByLink(tokenOf(submitted, control), "VAO", "à compléter");

        ReviewerView view = submission.openLink(tokenOf(submitted, control)).orElseThrow();

        assertThat(view.myLastOpinion()).isPresent();
        assertThat(view.myLastOpinion().get().comment()).isEqualTo("à compléter");
    }

    @Test
    void contractorCanRecordAnAnswerOnBehalfAndTheProofSaysSo() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));

        RevisionStatus status = submission.recordOpinionOnBehalf(submitted.revision().id(), control, "VSO",
                "reçu par téléphone", "khawla");

        assertThat(status).isEqualTo(RevisionStatus.APPROVED);
        Opinion opinion = reviews.history(submitted.revision().id()).get(0);
        assertThat(opinion.isOnBehalf()).isTrue();
        assertThat(opinion.enteredBy()).isEqualTo("khawla");
        assertThat(auditLog.findByTarget("REVISION", submitted.revision().id().value()))
                .filteredOn(e -> e.type().equals("OpinionGiven"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.actor()).isEqualTo("khawla");
                    assertThat(e.payload()).contains("onBehalfOf").contains(control.value().toString());
                });
    }

    @Test
    void recordingOnBehalfRequiresToKnowWhoEnteredIt() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));

        assertThatThrownBy(() -> submission.recordOpinionOnBehalf(submitted.revision().id(), control, "VSO", "", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordingOnBehalfOfSomeoneNotExpectedIsRefused() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));

        assertThatThrownBy(() -> submission.recordOpinionOnBehalf(submitted.revision().id(), structure, "VSO", "", "khawla"))
                .isInstanceOf(UnexpectedReviewerException.class);
    }

    @Test
    void aSecondLinkIssuedForTheSameReviewerReplacesTheFirst() {
        SubmittedRevision submitted = submission.submitForReview(request("A", Set.of(control)));
        String oldToken = tokenOf(submitted, control);

        IssuedLink fresh = links.issue(submitted.revision().id(), control, ACTOR);

        assertThat(submission.openLink(oldToken)).isEmpty();
        assertThat(submission.openLink(fresh.token().value())).isPresent();
    }
}
