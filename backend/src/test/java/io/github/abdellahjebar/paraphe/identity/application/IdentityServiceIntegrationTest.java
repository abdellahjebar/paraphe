package io.github.abdellahjebar.paraphe.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.identity.application.AuthenticationResult.Outcome;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.identity.domain.UserId;
import io.github.abdellahjebar.paraphe.identity.domain.WeakPasswordException;
import io.github.abdellahjebar.paraphe.projects.application.ProjectAccess;
import io.github.abdellahjebar.paraphe.projects.application.ProjectNotFoundException;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.support.MutableClock;
import io.github.abdellahjebar.paraphe.support.TestClockConfiguration;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class IdentityServiceIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private UserAdminService admin;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectAccess access;

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MutableClock clock;

    private String email;

    @BeforeEach
    void setUp() {
        clock.reset();
        email = "user-" + UUID.randomUUID() + "@example.org";
    }

    private AppUser create() {
        return admin.createUser(email, "Khawla", PASSWORD, false, "test");
    }

    @Test
    void createdAccountStoresOnlyAHashOfThePassword() {
        AppUser user = create();

        String stored = jdbc.sql("SELECT password_hash FROM app_user WHERE id = :id")
                .param("id", user.id().value()).query(String.class).single();

        assertThat(stored).startsWith("$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void emailsAreUniqueWhateverTheirCase() {
        create();

        assertThatThrownBy(() -> admin.createUser(email.toUpperCase(), "Autre", PASSWORD, false, "test"))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void weakPasswordIsRefusedAndNothingIsCreated() {
        assertThatThrownBy(() -> admin.createUser(email, "Khawla", "short", false, "test"))
                .isInstanceOf(WeakPasswordException.class);

        assertThat(jdbc.sql("SELECT count(*) FROM app_user WHERE email = :e").param("e", email)
                .query(Integer.class).single()).isZero();
    }

    @Test
    void databaseRefusesAnEmailThatIsNotNormalised() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO app_user (id, email, display_name, password_hash, created_at)
                VALUES (:id, 'UPPER@EXAMPLE.ORG', 'X', 'h', now())
                """).param("id", UUID.randomUUID()).update())
                .hasMessageContaining("app_user_email_normalised");
    }

    @Test
    void correctCredentialsSignIn() {
        AppUser user = create();

        AuthenticationResult result = authentication.authenticate(email, PASSWORD, "10.0.0.1");

        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.user().id()).isEqualTo(user.id());
    }

    @Test
    void emailMatchingIgnoresCaseAndSurroundingSpaces() {
        create();

        assertThat(authentication.authenticate("  " + email.toUpperCase() + " ", PASSWORD, "10.0.0.1").outcome())
                .isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void wrongPasswordAndUnknownAccountGiveTheSameOutcome() {
        create();

        AuthenticationResult wrong = authentication.authenticate(email, "not the password at all", "10.0.0.2");
        AuthenticationResult unknown = authentication.authenticate("nobody-" + UUID.randomUUID() + "@example.org",
                PASSWORD, "10.0.0.3");

        assertThat(wrong).isEqualTo(unknown);
        assertThat(wrong.outcome()).isEqualTo(Outcome.INVALID_CREDENTIALS);
    }

    @Test
    void aDeactivatedAccountCannotSignIn() {
        AppUser user = create();
        jdbc.sql("UPDATE app_user SET active = FALSE WHERE id = :id").param("id", user.id().value()).update();

        assertThat(authentication.authenticate(email, PASSWORD, "10.0.0.4").outcome())
                .isEqualTo(Outcome.INVALID_CREDENTIALS);
    }

    @Test
    void signInIsRecordedAndFailuresAreJournaledWithoutThePassword() {
        AppUser user = create();

        authentication.authenticate(email, "wrong password entirely", "10.0.0.5");
        authentication.authenticate(email, PASSWORD, "10.0.0.5");

        assertThat(auditLog.findByTarget("USER", user.id().value()))
                .extracting(AuditEvent::type)
                .containsExactly("UserCreated", "LoginFailed", "LoginSucceeded");
        assertThat(auditLog.findByTarget("USER", user.id().value()))
                .noneMatch(event -> event.payload().contains(PASSWORD) || event.payload().contains("wrong password"));
        assertThat(jdbc.sql("SELECT last_login_at FROM app_user WHERE id = :id")
                .param("id", user.id().value()).query(java.time.OffsetDateTime.class).optional()).isPresent();
    }

    @Test
    void accountIsLockedAfterFiveFailuresEvenForTheRightPassword() {
        create();
        for (int i = 0; i < 5; i++) {
            assertThat(authentication.authenticate(email, "wrong password " + i, "10.1.0." + i).outcome())
                    .isEqualTo(Outcome.INVALID_CREDENTIALS);
        }

        assertThat(authentication.authenticate(email, PASSWORD, "10.1.0.99").outcome()).isEqualTo(Outcome.LOCKED);
    }

    @Test
    void lockExpiresWithTime() {
        create();
        for (int i = 0; i < 5; i++) {
            authentication.authenticate(email, "wrong password " + i, "10.2.0." + i);
        }

        clock.advance(Duration.ofMinutes(16));

        assertThat(authentication.authenticate(email, PASSWORD, "10.2.0.99").outcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void anUnknownAccountLocksLikeARealOneSoLockoutRevealsNothing() {
        String ghost = "ghost-" + UUID.randomUUID() + "@example.org";
        for (int i = 0; i < 5; i++) {
            authentication.authenticate(ghost, "wrong password " + i, "10.3.0." + i);
        }

        assertThat(authentication.authenticate(ghost, PASSWORD, "10.3.0.99").outcome()).isEqualTo(Outcome.LOCKED);
    }

    @Test
    void oneClientHammeringManyAccountsIsLockedOut() {
        for (int i = 0; i < 5; i++) {
            authentication.authenticate("someone-" + i + "-" + UUID.randomUUID() + "@example.org",
                    "wrong password", "203.0.113.7");
        }

        assertThat(authentication.authenticate("another-" + UUID.randomUUID() + "@example.org", PASSWORD,
                "203.0.113.7").outcome()).isEqualTo(Outcome.LOCKED);
    }

    @Test
    void passwordChangeNeedsTheCurrentPasswordAndAcceptableNewOne() {
        AppUser user = create();

        assertThatThrownBy(() -> admin.changePassword(user.id(), "not the current one", "another good passphrase"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> admin.changePassword(user.id(), PASSWORD, "short"))
                .isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> admin.changePassword(user.id(), PASSWORD, PASSWORD))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void afterAChangeOnlyTheNewPasswordWorksAndTheObligationIsLifted() {
        AppUser user = admin.createUser(email, "Khawla", PASSWORD, true, "admin");
        assertThat(user.mustChangePassword()).isTrue();

        admin.changePassword(user.id(), PASSWORD, "a brand new passphrase");

        assertThat(authentication.authenticate(email, PASSWORD, "10.4.0.1").outcome())
                .isEqualTo(Outcome.INVALID_CREDENTIALS);
        AuthenticationResult result = authentication.authenticate(email, "a brand new passphrase", "10.4.0.2");
        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.user().mustChangePassword()).isFalse();
        assertThat(auditLog.findByTarget("USER", user.id().value()))
                .extracting(AuditEvent::type).contains("PasswordChanged");
    }

    @Test
    void passwordChangeOfAnUnknownUserIsReported() {
        assertThatThrownBy(() -> admin.changePassword(UserId.random(), PASSWORD, "another good passphrase"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void firstAccountIsCreatedOnlyWhenNoneExists() {
        create();

        assertThat(admin.createFirstUserIfNone("second-" + UUID.randomUUID() + "@example.org", "Autre", PASSWORD))
                .isEmpty();
    }

    @Test
    void creatingAProjectForAUserMakesThemMember() {
        AppUser user = create();

        Project project = projectService.createProjectFor(user.id().value(), "Chantier " + UUID.randomUUID(), "test");

        assertThat(access.isMember(project.id(), user.id().value())).isTrue();
        assertThat(access.projectsOf(user.id().value())).extracting(Project::id).contains(project.id());
    }

    @Test
    void nonMemberLearnsNothingAboutAProject() {
        AppUser owner = create();
        AppUser other = admin.createUser("other-" + UUID.randomUUID() + "@example.org", "Autre", PASSWORD, false, "test");
        Project project = projectService.createProjectFor(owner.id().value(), "Chantier " + UUID.randomUUID(), "test");

        assertThatThrownBy(() -> access.requireMember(project.id(), other.id().value()))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> access.requireMember(ProjectId.random(), other.id().value()))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThat(access.projectsOf(other.id().value())).isEmpty();
    }

    @Test
    void grantingAccessIsJournaledAndRepeatable() {
        AppUser owner = create();
        AppUser colleague = admin.createUser("col-" + UUID.randomUUID() + "@example.org", "Collègue", PASSWORD, false, "test");
        Project project = projectService.createProjectFor(owner.id().value(), "Chantier " + UUID.randomUUID(), "test");

        access.grant(project.id(), colleague.id().value(), "khawla");
        access.grant(project.id(), colleague.id().value(), "khawla");

        assertThat(access.isMember(project.id(), colleague.id().value())).isTrue();
        assertThat(auditLog.findByTarget("PROJECT", project.id().value()))
                .extracting(AuditEvent::type).contains("MemberGranted");
    }
}
