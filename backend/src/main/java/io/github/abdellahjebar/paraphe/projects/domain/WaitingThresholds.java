package io.github.abdellahjebar.paraphe.projects.domain;

/**
 * The days of waiting from which a silent reviewer is to be chased, and from which they are late.
 * A reviewer who has answered is never concerned: only an expected reviewer without an answer waits.
 */
public record WaitingThresholds(int chaseFromDays, int lateFromDays) {

    public static final int MAX_DAYS = 365;

    /** A week is normal, two weeks is not. */
    public static final WaitingThresholds DEFAULT = new WaitingThresholds(8, 15);

    public WaitingThresholds {
        if (chaseFromDays < 1) {
            throw new IllegalArgumentException("chasing starts from at least 1 day of waiting");
        }
        if (lateFromDays <= chaseFromDays) {
            throw new IllegalArgumentException("a reply is late only after the day it is chased");
        }
        if (lateFromDays > MAX_DAYS) {
            throw new IllegalArgumentException("thresholds are at most " + MAX_DAYS + " days");
        }
    }

    public Attention attentionFor(long waitingDays) {
        if (waitingDays >= lateFromDays) {
            return Attention.LATE;
        }
        if (waitingDays >= chaseFromDays) {
            return Attention.CHASE;
        }
        return Attention.NONE;
    }
}
