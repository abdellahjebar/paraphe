package io.github.abdellahjebar.paraphe.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdentityDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private AppUser user(String email, String name) {
        return new AppUser(UserId.random(), email, name, "$2a$12$hash", true, false, NOW);
    }

    @Test
    void emailIsStoredInOneNormalisedForm() {
        assertThat(user("  Khawla@Example.ORG ", "Khawla").email()).isEqualTo("khawla@example.org");
    }

    @Test
    void invalidEmailsAreRejected() {
        for (String email : new String[] {"", "   ", "no-at-sign", "a@b", "a b@c.org", "@c.org", "a@@c.org"}) {
            assertThatThrownBy(() -> user(email, "X")).as(email).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void displayNameMustBePresentAndBounded() {
        assertThatThrownBy(() -> user("a@b.org", "  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user("a@b.org", "x".repeat(101))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userNeverPrintsItsPasswordHash() {
        AppUser user = new AppUser(UserId.random(), "a@b.org", "A", "$2a$12$SECRETHASHVALUE", true, false, NOW);

        assertThat(user.toString()).doesNotContain("SECRETHASHVALUE").doesNotContain("$2a$");
    }

    @Test
    void normalizeEmailIsNullSafe() {
        assertThat(AppUser.normalizeEmail(null)).isEmpty();
    }

    @Test
    void aReasonablePassphraseIsAccepted() {
        assertThatCode(() -> PasswordPolicy.check("correct horse battery staple", "k@example.org"))
                .doesNotThrowAnyException();
    }

    @Test
    void shortOrEmptyPasswordsAreRefused() {
        for (String password : new String[] {null, "", "   ", "short", "elevenchars"}) {
            assertThatThrownBy(() -> PasswordPolicy.check(password, "k@example.org"))
                    .as("password '%s'", password)
                    .isInstanceOf(WeakPasswordException.class);
        }
    }

    @Test
    void passwordEqualToTheEmailOrItsLocalPartIsRefused() {
        assertThatThrownBy(() -> PasswordPolicy.check("khawla.lafrid@example.org", "Khawla.Lafrid@example.org"))
                .isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("khawla.lafrid", "khawla.lafrid@example.org"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void repetitivePasswordIsRefused() {
        assertThatThrownBy(() -> PasswordPolicy.check("aaaaaaaaaaaaaaaa", "k@example.org"))
                .isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("abababababababab", "k@example.org"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void passwordTooLongForTheHashingAlgorithmIsRefusedNotTruncated() {
        assertThatThrownBy(() -> PasswordPolicy.check("é".repeat(40), "k@example.org"))
                .isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("x1".repeat(40), "k@example.org"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void leadingOrTrailingSpaceIsRefused() {
        assertThatThrownBy(() -> PasswordPolicy.check(" a long enough passphrase", "k@example.org"))
                .isInstanceOf(WeakPasswordException.class);
    }
}
