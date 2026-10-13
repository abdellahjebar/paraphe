package io.github.abdellahjebar.paraphe.reviews.domain;

/** Where a review link stands. Always computed from facts, never stored. */
public enum LinkStatus {
    /** Sent, not opened yet. */
    ISSUED,
    /** Opened at least once, no answer yet. */
    OPENED,
    /** The reviewer has given an opinion on the revision. */
    ANSWERED,
    /** Past its expiry date without being revoked. */
    EXPIRED,
    /** Cancelled by the contractor, or replaced by a newer link. */
    REVOKED
}
