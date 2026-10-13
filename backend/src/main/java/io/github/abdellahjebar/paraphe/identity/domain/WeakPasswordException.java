package io.github.abdellahjebar.paraphe.identity.domain;

public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String reason) {
        super(reason);
    }
}
