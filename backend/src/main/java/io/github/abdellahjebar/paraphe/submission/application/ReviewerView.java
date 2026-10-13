package io.github.abdellahjebar.paraphe.submission.application;

import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What a reviewer sees when opening their link. It deliberately leaves out the other reviewers
 * and the global status of the revision.
 */
public record ReviewerView(
        String documentTitle,
        String revisionLabel,
        int revisionSequence,
        boolean revisionSuperseded,
        Instant linkExpiresAt,
        List<OpinionStatus> availableStatuses,
        Optional<Opinion> myLastOpinion) {
}
