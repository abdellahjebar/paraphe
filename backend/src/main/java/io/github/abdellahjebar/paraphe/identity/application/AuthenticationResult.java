package io.github.abdellahjebar.paraphe.identity.application;

import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import java.util.Optional;

/**
 * Outcome of a sign-in attempt. "Unknown account" and "wrong password" are the same outcome on
 * purpose: nothing here tells an attacker which emails have an account.
 */
public record AuthenticationResult(Outcome outcome, AppUser user) {

    public enum Outcome {
        SUCCESS,
        INVALID_CREDENTIALS,
        /** Too many recent failures: no password was even checked. */
        LOCKED
    }

    public static AuthenticationResult success(AppUser user) {
        return new AuthenticationResult(Outcome.SUCCESS, user);
    }

    public static AuthenticationResult invalid() {
        return new AuthenticationResult(Outcome.INVALID_CREDENTIALS, null);
    }

    public static AuthenticationResult locked() {
        return new AuthenticationResult(Outcome.LOCKED, null);
    }

    public Optional<AppUser> authenticatedUser() {
        return outcome == Outcome.SUCCESS ? Optional.of(user) : Optional.empty();
    }
}
