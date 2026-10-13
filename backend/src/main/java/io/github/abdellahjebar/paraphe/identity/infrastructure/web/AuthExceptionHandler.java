package io.github.abdellahjebar.paraphe.identity.infrastructure.web;

import io.github.abdellahjebar.paraphe.identity.application.InvalidCredentialsException;
import io.github.abdellahjebar.paraphe.identity.domain.WeakPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error mapping for the sign-in endpoints only. The general API mapping arrives with the API. */
@RestControllerAdvice(basePackageClasses = AuthController.class)
class AuthExceptionHandler {

    @ExceptionHandler(WeakPasswordException.class)
    ProblemDetail weakPassword(WeakPasswordException e) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        detail.setTitle("Password not accepted");
        detail.setDetail(e.getMessage());
        return detail;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail wrongCurrentPassword(InvalidCredentialsException e) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("The current password is not correct");
        return detail;
    }
}
