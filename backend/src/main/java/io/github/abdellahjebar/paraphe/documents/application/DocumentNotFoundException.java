package io.github.abdellahjebar.paraphe.documents.application;

import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(DocumentId id) {
        super("No document %s".formatted(id.value()));
    }
}
