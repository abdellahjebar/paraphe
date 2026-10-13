package io.github.abdellahjebar.paraphe.projects.application;

public class WorkPackageNotFoundException extends RuntimeException {

    public WorkPackageNotFoundException(String code) {
        super("No work package '%s' in this project".formatted(code));
    }
}
