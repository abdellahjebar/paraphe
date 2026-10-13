package io.github.abdellahjebar.paraphe.projects.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.projects.domain.DefaultStatuses;
import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.projects.domain.WorkPackage;
import io.github.abdellahjebar.paraphe.projects.port.ProjectRepository;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuration of a project: work packages, participants, statuses and default reviewers.
 * Every change is written to the audit journal in the same transaction.
 */
@Service
@Transactional
public class ProjectService {

    static final String TARGET_TYPE = "PROJECT";

    private final ProjectRepository projects;
    private final AuditLog auditLog;
    private final Clock clock;

    ProjectService(ProjectRepository projects, AuditLog auditLog, Clock clock) {
        this.projects = projects;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public Project createProject(String name, String actor) {
        Project project = new Project(ProjectId.random(), name, clock.instant());
        projects.create(project, DefaultStatuses.all());
        record("ProjectCreated", actor, project.id(), JsonPayload.of("name", project.name()));
        return project;
    }

    public WorkPackage addWorkPackage(ProjectId projectId, String code, String label, String actor) {
        requireProject(projectId);
        WorkPackage workPackage = new WorkPackage(code, label);
        if (projects.hasWorkPackage(projectId, workPackage.code())) {
            throw new DuplicateCodeException("Work package", workPackage.code());
        }
        projects.addWorkPackage(projectId, workPackage);
        record("WorkPackageAdded", actor, projectId, JsonPayload.of("code", workPackage.code()));
        return workPackage;
    }

    public Participant addParticipant(ProjectId projectId, String name, ReviewerRole role,
                                      String email, String phone, String actor) {
        requireProject(projectId);
        Participant participant = new Participant(ReviewerId.random(), projectId, name, role, email, phone, true);
        projects.addParticipant(participant);
        record("ParticipantAdded", actor, projectId,
                JsonPayload.of("participantId", participant.id().value().toString(), "role", role.name()));
        return participant;
    }

    public void deactivateParticipant(ProjectId projectId, ReviewerId participantId, String actor) {
        requireActiveParticipant(projectId, participantId);
        projects.deactivateParticipant(projectId, participantId);
        record("ParticipantDeactivated", actor, projectId,
                JsonPayload.of("participantId", participantId.value().toString()));
    }

    public OpinionStatus defineStatus(ProjectId projectId, String code, String label,
                                      OpinionEffect effect, String actor) {
        requireProject(projectId);
        OpinionStatus status = new OpinionStatus(code.strip(), label, effect);
        if (projects.hasStatus(projectId, status.code())) {
            throw new DuplicateCodeException("Status", status.code());
        }
        projects.addStatus(projectId, status);
        record("StatusDefined", actor, projectId,
                JsonPayload.of("code", status.code(), "effect", effect.name()));
        return status;
    }

    /** Past opinions are unaffected: each keeps the status it was given with. */
    public void deactivateStatus(ProjectId projectId, String code, String actor) {
        requireProject(projectId);
        if (!projects.hasStatus(projectId, code)) {
            throw new IllegalArgumentException("No status '%s' in this project".formatted(code));
        }
        projects.deactivateStatus(projectId, code);
        record("StatusDeactivated", actor, projectId, JsonPayload.of("code", code));
    }

    /** Sets who is pre-selected when a revision of this work package is sent. */
    public void setDefaultReviewers(ProjectId projectId, String workPackageCode,
                                    Set<ReviewerId> reviewers, String actor) {
        requireProject(projectId);
        if (!projects.hasWorkPackage(projectId, workPackageCode)) {
            throw new WorkPackageNotFoundException(workPackageCode);
        }
        for (ReviewerId reviewer : reviewers) {
            requireActiveParticipant(projectId, reviewer);
        }
        projects.setDefaultReviewers(projectId, workPackageCode, reviewers);
        record("DefaultReviewersSet", actor, projectId,
                JsonPayload.of("workPackage", workPackageCode, "count", String.valueOf(reviewers.size())));
    }

    @Transactional(readOnly = true)
    public Set<ReviewerId> expectedReviewersFor(ProjectId projectId, String workPackageCode) {
        requireProject(projectId);
        if (!projects.hasWorkPackage(projectId, workPackageCode)) {
            throw new WorkPackageNotFoundException(workPackageCode);
        }
        return projects.defaultReviewers(projectId, workPackageCode);
    }

    @Transactional(readOnly = true)
    public List<OpinionStatus> statuses(ProjectId projectId) {
        requireProject(projectId);
        return projects.activeStatuses(projectId);
    }

    @Transactional(readOnly = true)
    public List<Participant> participants(ProjectId projectId) {
        requireProject(projectId);
        return projects.participants(projectId);
    }

    @Transactional(readOnly = true)
    public List<WorkPackage> workPackages(ProjectId projectId) {
        requireProject(projectId);
        return projects.workPackages(projectId);
    }

    private void requireProject(ProjectId projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private void requireActiveParticipant(ProjectId projectId, ReviewerId participantId) {
        boolean active = projects.findParticipant(projectId, participantId)
                .map(Participant::active)
                .orElse(false);
        if (!active) {
            throw new ParticipantNotInProjectException(participantId);
        }
    }

    private void record(String type, String actor, ProjectId projectId, String payload) {
        auditLog.append(AuditEvent.of(type, actor, TARGET_TYPE, projectId.value(), payload, clock.instant()));
    }
}
