package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecurityComponentsTest {

    @Test
    void hashingUsesCostTwelveByDefault() {
        BCryptPasswordHasher hasher = new BCryptPasswordHasher(12, false);

        assertThat(hasher.hash("a long passphrase")).startsWith("$2a$12$");
    }

    @Test
    void aWeakCostIsRefusedUnlessExplicitlyAllowed() {
        assertThatThrownBy(() -> new BCryptPasswordHasher(4, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new BCryptPasswordHasher(4, true).hash("a long passphrase")).startsWith("$2a$04$");
    }

    @Test
    void hashingChecksPasswordsAndNeverStoresThemInClear() {
        BCryptPasswordHasher hasher = new BCryptPasswordHasher(4, true);
        String hash = hasher.hash("a long passphrase");

        assertThat(hash).doesNotContain("passphrase");
        assertThat(hasher.matches("a long passphrase", hash)).isTrue();
        assertThat(hasher.matches("another passphrase", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
        assertThat(hasher.matches("a long passphrase", null)).isFalse();
    }

    @Test
    void twoHashesOfTheSamePasswordDiffer() {
        BCryptPasswordHasher hasher = new BCryptPasswordHasher(4, true);

        assertThat(hasher.hash("a long passphrase")).isNotEqualTo(hasher.hash("a long passphrase"));
    }

    @Test
    void burningTimeNeverFailsEvenOnOddInput() {
        BCryptPasswordHasher hasher = new BCryptPasswordHasher(4, true);

        hasher.burnTime(null);
        hasher.burnTime("");
        hasher.burnTime("x".repeat(500));
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
