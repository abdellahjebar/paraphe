package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.support.OwnerJdbc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.reviews.domain.LinkStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.reviews.domain.UnexpectedReviewerException;
import io.github.abdellahjebar.paraphe.support.MutableClock;
import io.github.abdellahjebar.paraphe.support.TestClockConfiguration;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class ReviewLinkServiceIntegrationTest {

    private static final String ACTOR = "contractor";
    private static final OpinionStatus VSO = new OpinionStatus("VSO", "Visé sans observation", OpinionEffect.APPROVING);

    @Autowired
    private ReviewLinkService linkService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private OwnerJdbc owner;

    @Autowired
    private MutableClock clock;

    private Project project;
    private ReviewerId control;
    private ReviewerId structure;
    private RevisionId revision;

    @BeforeEach
    void setUp() {
        clock.reset();
        project = fixtures.project();
        control = fixtures.participant(project.id(), ReviewerRole.CONTROL_OFFICE).id();
        structure = fixtures.participant(project.id(), ReviewerRole.ENGINEERING_OFFICE).id();
        revision = fixtures.revision(project.id()).id();
        reviewService.open(revision, Set.of(control, structure), ACTOR);
    }

    private List<String> eventTypes() {
        return auditLog.findByTarget("REVISION", revision.value()).stream().map(AuditEvent::type).toList();
    }

    @Test
    void issuedTokenResolvesToItsRevisionAndReviewer() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);

        ResolvedLink resolved = linkService.resolve(issued.token().value()).orElseThrow();

        assertThat(resolved.revisionId()).isEqualTo(revision);
        assertThat(resolved.reviewerId()).isEqualTo(control);
        assertThat(resolved.linkId()).isEqualTo(issued.id());
    }

    @Test
    void twoReviewersGetDifferentTokensForTheSameRevision() {
        IssuedLink first = linkService.issue(revision, control, ACTOR);
        IssuedLink second = linkService.issue(revision, structure, ACTOR);

        assertThat(first.token()).isNotEqualTo(second.token());
        assertThat(linkService.resolve(first.token().value()).orElseThrow().reviewerId()).isEqualTo(control);
        assertThat(linkService.resolve(second.token().value()).orElseThrow().reviewerId()).isEqualTo(structure);
    }

    @Test
    void theTokenIsNeverStoredOnlyItsHash() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);
        String token = issued.token().value();

        Integer asHash = jdbc.sql("SELECT count(*) FROM review_link WHERE token_hash = :t")
                .param("t", token).query(Integer.class).single();
        Integer inJournal = jdbc.sql("SELECT count(*) FROM audit_event WHERE payload::text LIKE :p OR actor LIKE :p")
                .param("p", "%" + token + "%").query(Integer.class).single();

        assertThat(asHash).isZero();
        assertThat(inJournal).isZero();
    }

    @Test
    void issuedLinkDoesNotPrintItsToken() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);

        assertThat(issued.toString()).doesNotContain(issued.token().value());
        assertThat(issued.path()).startsWith("/r/").endsWith(issued.token().value());
    }

    @Test
    void unknownTokenIsRefusedAndLeavesNoTrace() {
        int before = eventTypes().size();

        assertThat(linkService.resolve(io.github.abdellahjebar.paraphe.reviews.domain.ReviewToken.generate().value()))
                .isEmpty();
        assertThat(linkService.resolve("not-a-token")).isEmpty();
        assertThat(linkService.resolve(null)).isEmpty();

        assertThat(eventTypes()).hasSize(before);
    }

    @Test
    void linkWorksUntilItExpiresThenIsRefusedAndTheRejectionIsJournaled() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);
        clock.advance(Duration.ofDays(30).minusSeconds(1));
        assertThat(linkService.resolve(issued.token().value())).isPresent();

        clock.advance(Duration.ofSeconds(2));

        assertThat(linkService.resolve(issued.token().value())).isEmpty();
        assertThat(auditLog.findByTarget("REVISION", revision.value()))
                .filteredOn(e -> e.type().equals("ReviewLinkRejected"))
                .singleElement()
                .satisfies(e -> assertThat(e.payload()).contains("EXPIRED").doesNotContain(issued.token().value()));
    }

    @Test
    void revokedLinkIsRefused() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);

        linkService.revoke(revision, control, ACTOR);

        assertThat(linkService.resolve(issued.token().value())).isEmpty();
        assertThat(eventTypes()).contains("ReviewLinkRevoked", "ReviewLinkRejected");
    }

    @Test
    void revokingWhenThereIsNoActiveLinkIsReported() {
        assertThatThrownBy(() -> linkService.revoke(revision, control, ACTOR))
                .isInstanceOf(ReviewLinkNotFoundException.class);
    }

    @Test
    void issuingANewLinkRevokesThePreviousOne() {
        IssuedLink old = linkService.issue(revision, control, ACTOR);
        IssuedLink fresh = linkService.issue(revision, control, ACTOR);

        assertThat(linkService.resolve(old.token().value())).isEmpty();
        assertThat(linkService.resolve(fresh.token().value())).isPresent();
    }

    @Test
    void aReviewerWhoIsNotExpectedCannotGetALink() {
        ReviewerId outsider = fixtures.participant(project.id(), ReviewerRole.ARCHITECT).id();

        assertThatThrownBy(() -> linkService.issue(revision, outsider, ACTOR))
                .isInstanceOf(UnexpectedReviewerException.class);
    }

    @Test
    void noLinkCanBeIssuedForARevisionWithoutAnOpenReview() {
        RevisionId notOpened = fixtures.revision(project.id()).id();

        assertThatThrownBy(() -> linkService.issue(notOpened, control, ACTOR))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    void openingRecordsTheFirstOpeningOnceAndJournalsEveryOpening() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);

        linkService.open(issued.token().value());
        var firstOpened = jdbc.sql("SELECT first_opened_at FROM review_link WHERE id = :id")
                .param("id", issued.id().value()).query(java.time.OffsetDateTime.class).single();
        clock.advance(Duration.ofHours(1));
        linkService.open(issued.token().value());
        var afterSecond = jdbc.sql("SELECT first_opened_at FROM review_link WHERE id = :id")
                .param("id", issued.id().value()).query(java.time.OffsetDateTime.class).single();

        assertThat(afterSecond).isEqualTo(firstOpened);
        assertThat(eventTypes().stream().filter("ReviewLinkOpened"::equals).count()).isEqualTo(2);
    }

    @Test
    void resolvingAloneDoesNotCountAsOpening() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);

        linkService.resolve(issued.token().value());

        assertThat(eventTypes()).doesNotContain("ReviewLinkOpened");
    }

    @Test
    void linkStatusFollowsTheLifeOfTheLink() {
        assertThat(linkService.statusOf(revision, control)).isEmpty();

        IssuedLink issued = linkService.issue(revision, control, ACTOR);
        assertThat(linkService.statusOf(revision, control)).contains(LinkStatus.ISSUED);

        linkService.open(issued.token().value());
        assertThat(linkService.statusOf(revision, control)).contains(LinkStatus.OPENED);

        reviewService.recordOpinion(revision, control, VSO, "");
        assertThat(linkService.statusOf(revision, control)).contains(LinkStatus.ANSWERED);

        clock.advance(Duration.ofDays(31));
        assertThat(linkService.statusOf(revision, control)).contains(LinkStatus.EXPIRED);
    }

    @Test
    void revokedStatusIsReported() {
        linkService.issue(revision, control, ACTOR);
        linkService.revoke(revision, control, ACTOR);

        assertThat(linkService.statusOf(revision, control)).contains(LinkStatus.REVOKED);
    }

    @Test
    void databaseAllowsOnlyOneActiveLinkPerReviewerAndRevision() {
        linkService.issue(revision, control, ACTOR);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO review_link (id, revision_id, reviewer_id, token_hash, issued_at, expires_at)
                VALUES (:id, :revision, :reviewer, :hash, now(), now() + interval '1 day')
                """)
                .param("id", UUID.randomUUID())
                .param("revision", revision.value())
                .param("reviewer", control.value())
                .param("hash", "a".repeat(64))
                .update())
                .hasMessageContaining("review_link_one_active");
    }

    @Test
    void databaseRefusesToDeleteOrRewriteALink() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);

        assertThatThrownBy(() -> owner.client().sql("DELETE FROM review_link WHERE id = :id")
                .param("id", issued.id().value()).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.client().sql("UPDATE review_link SET token_hash = :h WHERE id = :id")
                .param("h", "b".repeat(64)).param("id", issued.id().value()).update())
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> owner.client().sql("UPDATE review_link SET expires_at = expires_at + interval '1 year' WHERE id = :id")
                .param("id", issued.id().value()).update())
                .hasMessageContaining("immutable");
    }

    @Test
    void databaseRefusesToUnrevokeALink() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);
        linkService.revoke(revision, control, ACTOR);

        assertThatThrownBy(() -> jdbc.sql("UPDATE review_link SET revoked_at = NULL WHERE id = :id")
                .param("id", issued.id().value()).update())
                .hasMessageContaining("write-once");
    }

    @Test
    void linkCannotPointAtAReviewerNotAskedOnThatRevision() {
        ReviewerId outsider = fixtures.participant(project.id(), ReviewerRole.ARCHITECT).id();

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO review_link (id, revision_id, reviewer_id, token_hash, issued_at, expires_at)
                VALUES (:id, :revision, :reviewer, :hash, now(), now() + interval '1 day')
                """)
                .param("id", UUID.randomUUID())
                .param("revision", revision.value())
                .param("reviewer", outsider.value())
                .param("hash", "c".repeat(64))
                .update())
                .hasMessageContaining("foreign key");
    }

    @Test
    void anAnswerGivenThroughTheLinkIsOnTheSameRevisionAsTheLink() {
        IssuedLink issued = linkService.issue(revision, control, ACTOR);
        ResolvedLink resolved = linkService.resolve(issued.token().value()).orElseThrow();

        var status = reviewService.recordOpinion(resolved.revisionId(), resolved.reviewerId(), VSO, "ok");

        assertThat(status.name()).isEqualTo("PENDING");
        Optional<LinkStatus> linkStatus = linkService.statusOf(revision, control);
        assertThat(linkStatus).contains(LinkStatus.ANSWERED);
    }
}
