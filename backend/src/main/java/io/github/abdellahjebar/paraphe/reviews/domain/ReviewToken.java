package io.github.abdellahjebar.paraphe.reviews.domain;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The secret carried by a review link: 256 random bits, URL-safe. It exists in clear only at the
 * moment it is issued; afterwards only its {@link TokenHash} is kept.
 */
public final class ReviewToken {

    /** 32 bytes in unpadded base64url. */
    public static final int LENGTH = 43;

    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{" + LENGTH + "}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    private ReviewToken(String value) {
        this.value = value;
    }

    public static ReviewToken generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new ReviewToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** True when the text has the shape of a token; a cheap check before any lookup. */
    public static boolean hasTokenShape(String candidate) {
        return candidate != null && SHAPE.matcher(candidate).matches();
    }

    public String value() {
        return value;
    }

    public TokenHash hash() {
        return TokenHash.of(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReviewToken token && token.value.equals(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** Never print a token by accident (logs, exceptions, debuggers). */
    @Override
    public String toString() {
        return "ReviewToken[****]";
    }
}
