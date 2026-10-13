package io.github.abdellahjebar.paraphe.identity.port;

/** Counts failed sign-in attempts per key (an account, an address) and says when to stop trying. */
public interface LoginAttemptLimiter {

    boolean isBlocked(String key);

    void recordFailure(String key);

    void reset(String key);
}
