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
import io.github.abdellahjebar.paraphe.submission.application.LinkRefusedException;
import io.github.abdellahjebar.paraphe.submission.application.NoReviewersException;
import io.github.abdellahjebar.paraphe.submission.application.UnknownStatusException;
import io.github.abdellahjebar.paraphe.shared.web.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
            LinkRefusedException.class,
            AccessDeniedException.class
    })
    ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Not found", null, ErrorCode.NOT_FOUND);
    }

    // ---- conflicts ---------------------------------------------------------------------------

    @ExceptionHandler({
            DuplicateCodeException.class,
            DuplicateRevisionLabelException.class,
            ReviewAlreadyOpenException.class,
            EmailAlreadyUsedException.class
    })
    ResponseEntity<ProblemDetail> conflict(RuntimeException e) {
        ErrorCode code = switch (e) {
            case DuplicateCodeException ignored -> ErrorCode.DUPLICATE_CODE;
            case DuplicateRevisionLabelException ignored -> ErrorCode.DUPLICATE_REVISION_LABEL;
            case ReviewAlreadyOpenException ignored -> ErrorCode.REVIEW_ALREADY_OPEN;
            default -> ErrorCode.EMAIL_ALREADY_USED;
        };
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage(), code);
    }

    // ---- uploads -----------------------------------------------------------------------------

    @ExceptionHandler(InvalidPdfException.class)
    ResponseEntity<ProblemDetail> notAPdf(InvalidPdfException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file is not a PDF", null, ErrorCode.NOT_A_PDF);
    }

    // The multipart limit (MaxUploadSizeExceededException) is already answered with 413 by Spring.
    @ExceptionHandler(FileTooLargeException.class)
    ResponseEntity<ProblemDetail> tooLarge() {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "The file is too large", null, ErrorCode.FILE_TOO_LARGE);
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
        ErrorCode code = switch (e) {
            case NoReviewersException ignored -> ErrorCode.NO_REVIEWERS;
            case UnknownStatusException ignored -> ErrorCode.UNKNOWN_STATUS;
            case ParticipantNotInProjectException ignored -> ErrorCode.PARTICIPANT_NOT_IN_PROJECT;
            case UnexpectedReviewerException ignored -> ErrorCode.UNEXPECTED_REVIEWER;
            case WeakPasswordException ignored -> ErrorCode.WEAK_PASSWORD;
            default -> ErrorCode.INVALID_REQUEST;
        };
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "The request cannot be processed", e.getMessage(), code);
        if (e instanceof WeakPasswordException weak) {
            response.getBody().setProperty("reason", weak.reason().name());
            if (weak.limit() != null) {
                response.getBody().setProperty("limit", weak.limit());
            }
        }
        return response;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> wrongCurrentPassword() {
        return problem(HttpStatus.BAD_REQUEST, "The current password is not correct", null,
                ErrorCode.INVALID_CURRENT_PASSWORD);
    }

    // ---- framework errors: a code is added to Spring's own problem ---------------------------

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                              HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", codeFor(ex, statusCode).name());
            if (ex instanceof MethodArgumentNotValidException invalid) {
                problem.setProperty("errors", fieldErrors(invalid.getBindingResult()));
            }
        }
        return response;
    }

    private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (ex instanceof MaxUploadSizeExceededException) {
            return ErrorCode.FILE_TOO_LARGE;
        }
        return switch (status.value()) {
            case 400 -> ErrorCode.MALFORMED_REQUEST;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.REQUEST_REFUSED;
        };
    }

    /** Field and a reason code, never the value that was refused. */
    private static List<Map<String, String>> fieldErrors(BindingResult result) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : result.getFieldErrors()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("field", error.getField());
            entry.put("code", fieldCode(error));
            errors.add(entry);
        }
        return errors;
    }

    private static String fieldCode(FieldError error) {
        String constraint = error.getCode() == null ? "" : error.getCode();
        return switch (constraint) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Size" -> error.getRejectedValue() instanceof CharSequence text && isLongerThanMax(error, text)
                    ? "TOO_LONG" : "TOO_SHORT";
            case "Min", "DecimalMin", "Positive", "PositiveOrZero" -> "TOO_SMALL";
            case "Max", "DecimalMax", "Negative", "NegativeOrZero" -> "TOO_LARGE";
            case "Email" -> "INVALID_EMAIL";
            case "Pattern" -> "INVALID_FORMAT";
            default -> "INVALID";
        };
    }

    private static boolean isLongerThanMax(FieldError error, CharSequence text) {
        Object[] arguments = error.getArguments();
        // For @Size the arguments are [field, max, min].
        if (arguments != null && arguments.length >= 2 && arguments[1] instanceof Integer max) {
            return text.length() > max;
        }
        return true;
    }

    // ---- anything else -----------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        String correlationId = UUID.randomUUID().toString();
        // The detail stays in the server log, keyed by the id the caller can quote.
        log.error("Unexpected failure, correlationId={}", correlationId, e);
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", null,
                ErrorCode.INTERNAL_ERROR);
        response.getBody().setProperty("correlationId", correlationId);
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                         ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        if (detail != null) {
            problem.setDetail(detail);
        }
        problem.setProperty("code", code.name());
        return ResponseEntity.status(status).body(problem);
    }
}
