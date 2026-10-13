package io.github.abdellahjebar.paraphe.identity.application;

/** The current password given when changing a password is wrong. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("The current password is not correct");
    }
}
