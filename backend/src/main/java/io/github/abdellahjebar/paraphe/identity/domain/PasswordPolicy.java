package io.github.abdellahjebar.paraphe.identity.domain;

import java.util.Locale;

/**
 * What a new password must satisfy, following NIST SP 800-63B-4: length rather than character classes,
 * a refusal list of common passwords, no rule about spaces or composition, no periodic change.
 *
 * <p>Length counts Unicode code points, so an accented passphrase is not penalised.
 */
public final class PasswordPolicy {

    /** A password used on its own, without a second factor. */
    public static final int MIN_LENGTH = 15;
    /** Bounds hashing cost; well above the 64 characters a verifier must accept. */
    public static final int MAX_LENGTH = 128;
    private static final int MIN_NAME_LENGTH = 4;
    private static final String SERVICE_NAME = "paraphe";

    private PasswordPolicy() {
    }

    public static void check(String password, String email) {
        check(password, email, PasswordBlocklist.NONE);
    }

    public static void check(String password, String email, PasswordBlocklist blocklist) {
        if (password == null || password.isEmpty()) {
            throw new WeakPasswordException(WeakPasswordException.Reason.EMPTY, "The password must not be empty");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            throw new WeakPasswordException(WeakPasswordException.Reason.TOO_SHORT, MIN_LENGTH,
                    "The password must have at least %d characters".formatted(MIN_LENGTH));
        }
        if (length > MAX_LENGTH) {
            throw new WeakPasswordException(WeakPasswordException.Reason.TOO_LONG, MAX_LENGTH,
                    "The password must have at most %d characters".formatted(MAX_LENGTH));
        }
        if (password.isBlank()) {
            throw new WeakPasswordException(WeakPasswordException.Reason.EMPTY, "The password must not be blank");
        }
        String normalizedEmail = AppUser.normalizeEmail(email);
        String localPart = normalizedEmail.contains("@")
                ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : normalizedEmail;
        String lowered = password.toLowerCase(Locale.ROOT);
        if (lowered.equals(normalizedEmail) || (!localPart.isEmpty() && lowered.equals(localPart))) {
            throw new WeakPasswordException(WeakPasswordException.Reason.SAME_AS_EMAIL,
                    "The password must not be the email address");
        }
        if ((localPart.length() >= MIN_NAME_LENGTH && lowered.contains(localPart)) || lowered.contains(SERVICE_NAME)) {
            throw new WeakPasswordException(WeakPasswordException.Reason.CONTAINS_NAME,
                    "The password must not contain the account name or the name of the service");
        }
        if (password.codePoints().distinct().count() < 5) {
            throw new WeakPasswordException(WeakPasswordException.Reason.TOO_REPETITIVE,
                    "The password is too repetitive");
        }
        if (blocklist.contains(password)) {
            throw new WeakPasswordException(WeakPasswordException.Reason.COMMON,
                    "The password is too common");
        }
    }
}
