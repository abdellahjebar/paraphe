package io.github.abdellahjebar.paraphe.shared.web;

/**
 * Why one field of a request was refused, in the {@code errors} list of a validation failure.
 * Like {@link ErrorCode}, it is part of the contract: a client chooses its wording from it.
 */
public enum FieldErrorCode {
    REQUIRED,
    TOO_LONG,
    TOO_SHORT,
    TOO_SMALL,
    TOO_LARGE,
    INVALID_EMAIL,
    INVALID_FORMAT,
    INVALID
}
