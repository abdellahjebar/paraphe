package io.github.abdellahjebar.paraphe.reviews.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The SHA-256 of a review token, as 64 lowercase hex characters. This is the only form of a token
 * that is ever stored or compared.
 */
public record TokenHash(String hex) {

    public TokenHash {
        Objects.requireNonNull(hex, "hex");
        if (!hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("a token hash is 64 lowercase hex characters");
        }
    }

    public static TokenHash of(String token) {
        Objects.requireNonNull(token, "token");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return new TokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }

    /** Never print the hash by accident. */
    @Override
    public String toString() {
        return "TokenHash[****]";
    }
}
