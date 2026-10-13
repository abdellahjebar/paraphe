package io.github.abdellahjebar.paraphe.identity.application;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("An account already exists for this email");
    }
}
