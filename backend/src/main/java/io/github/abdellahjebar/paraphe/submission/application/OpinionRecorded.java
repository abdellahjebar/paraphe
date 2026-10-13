package io.github.abdellahjebar.paraphe.submission.application;

import io.github.abdellahjebar.paraphe.reviews.domain.RevisionStatus;

/**
 * Outcome of an opinion given through a link.
 *
 * @param revisionSuperseded the revision has a newer one; the opinion is kept as a fact but the
 *     reviewer should be told it concerns an older version
 */
public record OpinionRecorded(RevisionStatus status, boolean revisionSuperseded) {
}
