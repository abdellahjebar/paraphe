package io.github.abdellahjebar.paraphe.identity.port;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);

    /**
     * Does the same work as {@link #matches} against a throwaway hash. Called when the account does
     * not exist, so a missing account and a wrong password take the same time.
     */
    void burnTime(String rawPassword);
}
