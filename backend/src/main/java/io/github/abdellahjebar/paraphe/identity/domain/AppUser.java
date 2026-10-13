package io.github.abdellahjebar.paraphe.identity.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A person who can sign in. The password is only ever present as an opaque hash.
 *
 * <p>{@code mustChangePassword} is true for an account just created by an administrator: until the
 * person chooses their own password, the account can do nothing else.
 */
public record AppUser(
        UserId id,
        String email,
        String displayName,
        String passwordHash,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt) {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;

    public AppUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(createdAt, "createdAt");
        email = normalizeEmail(email);
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email is not valid");
        }
        displayName = displayName.strip();
        if (displayName.isEmpty() || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("display name must be 1 to " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
    }

    /** The one form emails are compared and stored in. Null-safe, so a missing email never matches. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Never print the hash by accident. */
    @Override
    public String toString() {
        return "AppUser[id=%s, email=%s, active=%s]".formatted(id.value(), email, active);
    }
}
