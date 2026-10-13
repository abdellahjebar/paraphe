package io.github.abdellahjebar.paraphe.projects.application;

import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;

/** The participant does not exist in the project, or is no longer active. */
public class ParticipantNotInProjectException extends RuntimeException {

    public ParticipantNotInProjectException(ReviewerId id) {
        super("Participant %s is not an active member of this project".formatted(id.value()));
    }
}
