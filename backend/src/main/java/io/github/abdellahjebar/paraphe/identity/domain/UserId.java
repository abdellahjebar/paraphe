package io.github.abdellahjebar.paraphe.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID());
    }
}
