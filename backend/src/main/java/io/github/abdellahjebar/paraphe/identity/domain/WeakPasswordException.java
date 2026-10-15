package io.github.abdellahjebar.paraphe.identity.domain;

/** A new password was refused. {@link #reason()} says why, so a client can explain it in its own words. */
public class WeakPasswordException extends RuntimeException {

    public enum Reason {
        EMPTY,
        TOO_SHORT,
        TOO_LONG,
        SAME_AS_EMAIL,
        CONTAINS_NAME,
        COMMON,
        TOO_REPETITIVE,
        SAME_AS_CURRENT
    }

    private final Reason reason;
    private final Integer limit;

    public WeakPasswordException(Reason reason, String message) {
        this(reason, null, message);
    }

    public WeakPasswordException(Reason reason, Integer limit, String message) {
        super(message);
        this.reason = reason;
        this.limit = limit;
    }

    public Reason reason() {
        return reason;
    }

    /** The length bound for TOO_SHORT and TOO_LONG, otherwise null. */
    public Integer limit() {
        return limit;
    }
}
