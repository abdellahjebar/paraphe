package io.github.abdellahjebar.paraphe.projects.application;

/** A work package or status code that already exists in the project. */
public class DuplicateCodeException extends RuntimeException {

    public DuplicateCodeException(String kind, String code) {
        super("%s '%s' already exists in this project".formatted(kind, code));
    }
}
