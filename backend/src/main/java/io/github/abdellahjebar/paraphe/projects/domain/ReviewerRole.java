package io.github.abdellahjebar.paraphe.projects.domain;

/**
 * What a participant is on the project. The roles carry different responsibilities: the architect
 * and the engineering offices review the design, the control office reviews conformity and safety.
 */
public enum ReviewerRole {
    ARCHITECT,
    ENGINEERING_OFFICE,
    CONTROL_OFFICE
}
