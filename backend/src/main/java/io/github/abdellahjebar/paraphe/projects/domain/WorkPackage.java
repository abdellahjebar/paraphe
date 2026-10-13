package io.github.abdellahjebar.paraphe.projects.domain;

import java.util.Objects;

/** A trade or part of the work of a project (for example "Lot 01", "GENERAL"). */
public record WorkPackage(String code, String label) {

    public WorkPackage {
        Objects.requireNonNull(code, "code");
        code = code.strip();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("work package code must not be blank");
        }
        label = label == null || label.isBlank() ? code : label.strip();
    }
}
