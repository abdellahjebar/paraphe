package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecurityComponentsTest {

    @Test
    void hashingUsesArgon2idWithTheOwaspMinimumByDefault() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(19456, false);

        assertThat(hasher.hash("a long passphrase")).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void aWeakMemorySizeIsRefusedUnlessExplicitlyAllowed() {
        assertThatThrownBy(() -> new Argon2idPasswordHasher(1024, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new Argon2idPasswordHasher(1024, true).hash("a long passphrase"))
                .startsWith("$argon2id$v=19$m=1024,");
    }

    @Test
    void hashingChecksPasswordsAndNeverStoresThemInClear() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(1024, true);
        String hash = hasher.hash("a long passphrase");

        assertThat(hash).doesNotContain("passphrase");
        assertThat(hasher.matches("a long passphrase", hash)).isTrue();
        assertThat(hasher.matches("another passphrase", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
        assertThat(hasher.matches("a long passphrase", null)).isFalse();
    }

    @Test
    void twoHashesOfTheSamePasswordDiffer() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(1024, true);

        assertThat(hasher.hash("a long passphrase")).isNotEqualTo(hasher.hash("a long passphrase"));
    }

    @Test
    void theSameTextTypedInTwoUnicodeFormsGivesTheSameLogin() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(1024, true);
        String composed = "café au lait chaud";
        String decomposed = "café au lait chaud";

        assertThat(hasher.matches(decomposed, hasher.hash(composed))).isTrue();
    }

    @Test
    void passwordsOverSeventyTwoBytesAreHashedWholeAndNotTruncated() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(1024, true);
        String longOne = "é".repeat(60) + "A";
        String sameStart = "é".repeat(60) + "B";
        String hash = hasher.hash(longOne);

        assertThat(hasher.matches(longOne, hash)).isTrue();
        assertThat(hasher.matches(sameStart, hash)).isFalse();
    }

    @Test
    void anOlderBcryptHashStillWorksAndIsFlaggedForReplacement() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(1024, true);
        String legacy = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("a long passphrase");

        assertThat(hasher.matches("a long passphrase", legacy)).isTrue();
        assertThat(hasher.matches("not the passphrase", legacy)).isFalse();
        assertThat(hasher.needsRehash(legacy)).isTrue();
        assertThat(hasher.needsRehash(hasher.hash("a long passphrase"))).isFalse();
    }

    @Test
    void aHashMadeWithOtherParametersIsFlaggedForReplacement() {
        String other = new Argon2idPasswordHasher(2048, true).hash("a long passphrase");

        assertThat(new Argon2idPasswordHasher(1024, true).needsRehash(other)).isTrue();
    }

    @Test
    void unknownHashFormatsNeverMatch() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(1024, true);

        assertThat(hasher.matches("a long passphrase", "plain text")).isFalse();
        assertThat(hasher.matches("a long passphrase", "{noop}a long passphrase")).isFalse();
    }

    @Test
    void burningTimeNeverFailsEvenOnOddInput() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(1024, true);

        hasher.burnTime(null);
        hasher.burnTime("");
        hasher.burnTime("x".repeat(500));
    }

    @Test
    void theShippedBlocklistLoadsAndIgnoresCaseAndSpaces() {
        ResourcePasswordBlocklist list = new ResourcePasswordBlocklist();

        assertThat(list.size()).isGreaterThan(100);
        assertThat(list.contains("PASSWORDPASSWORD")).isTrue();
        assertThat(list.contains("pass word pass word")).isTrue();
        assertThat(list.contains("a long passphrase nobody else uses")).isFalse();
        assertThat(list.contains(null)).isFalse();
    }

    @Test
    void accountIsBlockedAfterTooManyFailuresThenFreedWhenTheyAgeOut() {
        MutableClock clock = new MutableClock();
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(clock, 3, 15);

        limiter.recordFailure("account:a");
        limiter.recordFailure("account:a");
        assertThat(limiter.isBlocked("account:a")).isFalse();
        limiter.recordFailure("account:a");
        assertThat(limiter.isBlocked("account:a")).isTrue();

        clock.advance(Duration.ofMinutes(16));

        assertThat(limiter.isBlocked("account:a")).isFalse();
    }

    @Test
    void failuresAreCountedPerKey() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(new MutableClock(), 2, 15);

        limiter.recordFailure("account:a");
        limiter.recordFailure("account:a");

        assertThat(limiter.isBlocked("account:a")).isTrue();
        assertThat(limiter.isBlocked("account:b")).isFalse();
    }

    @Test
    void aSuccessfulSignInClearsTheFailures() {
        InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(new MutableClock(), 2, 15);
        limiter.recordFailure("account:a");
        limiter.recordFailure("account:a");

        limiter.reset("account:a");

        assertThat(limiter.isBlocked("account:a")).isFalse();
    }

    @Test
    void requestLimiterAllowsTheBudgetThenRefusesUntilTheWindowPasses() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(clock, 3, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        assertThat(limiter.tryAcquire("other")).isTrue();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("ip")).isTrue();
    }
}
