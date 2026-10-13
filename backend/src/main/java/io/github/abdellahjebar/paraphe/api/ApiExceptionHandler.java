package io.github.abdellahjebar.paraphe.api;

import io.github.abdellahjebar.paraphe.documents.application.DocumentNotFoundException;
import io.github.abdellahjebar.paraphe.documents.application.DuplicateRevisionLabelException;
import io.github.abdellahjebar.paraphe.documents.application.RevisionNotFoundException;
import io.github.abdellahjebar.paraphe.documents.domain.FileTooLargeException;
import io.github.abdellahjebar.paraphe.documents.domain.InvalidPdfException;
import io.github.abdellahjebar.paraphe.identity.application.EmailAlreadyUsedException;
import io.github.abdellahjebar.paraphe.identity.application.InvalidCredentialsException;
import io.github.abdellahjebar.paraphe.identity.application.UserNotFoundException;
import io.github.abdellahjebar.paraphe.identity.domain.WeakPasswordException;
import io.github.abdellahjebar.paraphe.projects.application.DuplicateCodeException;
import io.github.abdellahjebar.paraphe.projects.application.ParticipantNotInProjectException;
import io.github.abdellahjebar.paraphe.projects.application.ProjectNotFoundException;
import io.github.abdellahjebar.paraphe.projects.application.WorkPackageNotFoundException;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewAlreadyOpenException;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewLinkNotFoundException;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewNotFoundException;
import io.github.abdellahjebar.paraphe.reviews.domain.UnexpectedReviewerException;
import io.github.abdellahjebar.paraphe.submission.application.NoReviewersException;
import io.github.abdellahjebar.paraphe.submission.application.UnknownStatusException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that turns what the application throws into the status and body the API promises.
 *
 * <p>Rules: a resource the caller may not use is "not found", exactly as if it did not exist;
 * business refusals carry their (safe, English) message; anything unexpected is a plain 500 with a
 * correlation id and no detail, because an unexpected failure is the one that may contain secrets.
 *
 * <p>It extends Spring's own handler so framework errors (malformed JSON, wrong method, missing
 * route...) keep their standard problem bodies instead of falling into the catch-all.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // ---- not found, and "not yours" which must look the same ---------------------------------

    @ExceptionHandler({
            ProjectNotFoundException.class,
            DocumentNotFoundException.class,
            RevisionNotFoundException.class,
            WorkPackageNotFoundException.class,
            ReviewNotFoundException.class,
            ReviewLinkNotFoundException.class,
            UserNotFoundException.class,
            AccessDeniedException.class
    })
    ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Not found", null);
    }

    // ---- conflicts ---------------------------------------------------------------------------

    @ExceptionHandler({
            DuplicateCodeException.class,
            DuplicateRevisionLabelException.class,
            ReviewAlreadyOpenException.class,
            EmailAlreadyUsedException.class
    })
    ResponseEntity<ProblemDetail> conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    // ---- uploads -----------------------------------------------------------------------------

    @ExceptionHandler(InvalidPdfException.class)
    ResponseEntity<ProblemDetail> notAPdf(InvalidPdfException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file is not a PDF", null);
    }

    // The multipart limit (MaxUploadSizeExceededException) is already answered with 413 by Spring.
    @ExceptionHandler(FileTooLargeException.class)
    ResponseEntity<ProblemDetail> tooLarge() {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "The file is too large", null);
    }

    // ---- refused input -----------------------------------------------------------------------

    @ExceptionHandler({
            NoReviewersException.class,
            UnknownStatusException.class,
            ParticipantNotInProjectException.class,
            UnexpectedReviewerException.class,
            WeakPasswordException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> unprocessable(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "The request cannot be processed", e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> wrongCurrentPassword() {
        return problem(HttpStatus.BAD_REQUEST, "The current password is not correct", null);
    }

    // ---- anything else -----------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        String correlationId = UUID.randomUUID().toString();
        // The detail stays in the server log, keyed by the id the caller can quote.
        log.error("Unexpected failure, correlationId={}", correlationId, e);
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", null);
        response.getBody().setProperty("correlationId", correlationId);
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        if (detail != null) {
            problem.setDetail(detail);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
