package io.github.abdellahjebar.paraphe.documents.application;

import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;

public class RevisionNotFoundException extends RuntimeException {

    public RevisionNotFoundException(RevisionId id) {
        super("No revision %s".formatted(id.value()));
    }
}
