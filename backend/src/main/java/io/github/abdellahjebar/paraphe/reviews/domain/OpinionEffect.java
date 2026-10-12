package io.github.abdellahjebar.paraphe.reviews.domain;

/**
 * What an opinion status means for the revision it is given on.
 * Status codes (VSO, VAO, VPI, NC...) are configurable per project; each one maps to an effect.
 */
public enum OpinionEffect {
    /** The reviewer approves (e.g. VSO). */
    APPROVING,
    /** The reviewer approves provided points are fixed (e.g. VAO). */
    APPROVING_WITH_RESERVES,
    /** The reviewer only takes note, nothing is validated or blocked (e.g. VPI). */
    INFORMATIVE,
    /** The reviewer rejects, the revision must be reworked (e.g. NC, REF). */
    BLOCKING
}
