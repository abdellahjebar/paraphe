package io.github.abdellahjebar.paraphe.projects.application;

import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(ProjectId id) {
        super("No project %s".formatted(id.value()));
    }
}
