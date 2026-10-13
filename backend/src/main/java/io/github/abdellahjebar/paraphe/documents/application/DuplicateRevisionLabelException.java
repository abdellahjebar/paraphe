package io.github.abdellahjebar.paraphe.documents.application;

public class DuplicateRevisionLabelException extends RuntimeException {

    public DuplicateRevisionLabelException(String label) {
        super("This document already has a revision '%s'".formatted(label));
    }
}
