package io.github.abdellahjebar.paraphe.projects.domain;

/** How much a reviewer's silence deserves the contractor's attention. */
public enum Attention {
    /** Within the normal delay. */
    NONE,
    /** Time to chase. */
    CHASE,
    /** Overdue. */
    LATE
}
