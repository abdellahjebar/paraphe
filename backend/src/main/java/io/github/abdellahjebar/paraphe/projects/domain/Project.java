package io.github.abdellahjebar.paraphe.projects.domain;

import java.time.Instant;
import java.util.Objects;

/** A construction site whose documents are being reviewed. */
public record Project(ProjectId id, String name, Instant createdAt) {

    public static final int MAX_NAME_LENGTH = 200;

    public Project {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("project name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("project name is longer than " + MAX_NAME_LENGTH);
        }
    }
}
