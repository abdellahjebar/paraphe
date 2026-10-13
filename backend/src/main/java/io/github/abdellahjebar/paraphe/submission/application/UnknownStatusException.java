package io.github.abdellahjebar.paraphe.submission.application;

/** The status code is not an active status of the project. */
public class UnknownStatusException extends RuntimeException {

    public UnknownStatusException(String code) {
        super("Status '%s' is not available in this project".formatted(code));
    }
}
