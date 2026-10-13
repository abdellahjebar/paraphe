package io.github.abdellahjebar.paraphe.identity.application;

import io.github.abdellahjebar.paraphe.identity.domain.UserId;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UserId id) {
        super("No user %s".formatted(id.value()));
    }
}
