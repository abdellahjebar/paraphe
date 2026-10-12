package io.github.abdellahjebar.paraphe.reviews.domain;

/** Global status of a revision. Always derived from opinions, never set by hand. */
public enum RevisionStatus {
    /** At least one expected reviewer has not answered yet. */
    PENDING,
    /** Everyone answered, with informative opinions only: noted, not approved. */
    ACKNOWLEDGED,
    /** Everyone answered and the revision is approved. */
    APPROVED,
    /** Everyone answered; approved, but at least one reserve must be lifted. */
    APPROVED_WITH_RESERVES,
    /** At least one reviewer blocked the revision: a new revision is needed. */
    TO_REVISE
}
