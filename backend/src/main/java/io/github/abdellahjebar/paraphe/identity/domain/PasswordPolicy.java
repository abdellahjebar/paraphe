package io.github.abdellahjebar.paraphe.identity.domain;

/**
 * What a new password must satisfy. Deliberately about length rather than character classes: a
 * long passphrase is stronger and easier to remember than a short "complex" one.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /** Keeps hashing cost bounded and stays under bcrypt's 72-byte input limit with room to spare. */
    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    public static void check(String password, String email) {
        if (password == null || password.isBlank()) {
            throw new WeakPasswordException("The password must not be empty");
        }
        if (password.length() < MIN_LENGTH) {
            throw new WeakPasswordException("The password must have at least %d characters".formatted(MIN_LENGTH));
        }
        if (password.length() > MAX_LENGTH) {
            throw new WeakPasswordException("The password must have at most %d characters".formatted(MAX_LENGTH));
        }
        // bcrypt only reads the first 72 bytes: refuse longer input rather than silently truncate it.
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new WeakPasswordException("The password is too long once encoded");
        }
        if (password.strip().length() != password.length()) {
            throw new WeakPasswordException("The password must not start or end with a space");
        }
        String normalizedEmail = AppUser.normalizeEmail(email);
        String localPart = normalizedEmail.contains("@")
                ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : normalizedEmail;
        String lowered = password.toLowerCase(java.util.Locale.ROOT);
        if (lowered.equals(normalizedEmail) || (!localPart.isEmpty() && lowered.equals(localPart))) {
            throw new WeakPasswordException("The password must not be the email address");
        }
        if (password.chars().distinct().count() < 5) {
            throw new WeakPasswordException("The password is too repetitive");
        }
    }
}
