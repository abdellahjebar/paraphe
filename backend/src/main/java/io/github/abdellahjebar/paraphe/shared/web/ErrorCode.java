package io.github.abdellahjebar.paraphe.shared.web;

/**
 * The machine-readable code carried by every error body, in the {@code code} property.
 *
 * <p>A client chooses its wording from the code, never from the {@code title} or {@code detail}
 * text, which are for developers and may change. Adding a code is a contract change; removing or
 * renaming one is a breaking change.
 */
public enum ErrorCode {

    // access
    AUTHENTICATION_REQUIRED,
    FORBIDDEN,
    NOT_FOUND,
    TOO_MANY_REQUESTS,
    TOO_MANY_ATTEMPTS,
    INVALID_CREDENTIALS,
    INVALID_CURRENT_PASSWORD,
    WEAK_PASSWORD,

    // the request itself
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_REQUEST,
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    UNSUPPORTED_MEDIA_TYPE,
    NOT_A_PDF,
    FILE_TOO_LARGE,

    // conflicts with what already exists
    DUPLICATE_CODE,
    DUPLICATE_REVISION_LABEL,
    REVIEW_ALREADY_OPEN,
    EMAIL_ALREADY_USED,

    // refused by a business rule
    NO_REVIEWERS,
    UNKNOWN_STATUS,
    PARTICIPANT_NOT_IN_PROJECT,
    UNEXPECTED_REVIEWER,

    // anything else
    REQUEST_REFUSED,
    INTERNAL_ERROR
}
