package io.github.abdellahjebar.paraphe.projects.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.port.ProjectRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may use which project.
 *
 * <p>A user who is not a member gets {@link ProjectNotFoundException}, exactly as if the project did
 * not exist: nobody learns that a project they cannot see is there.
 */
@Service
@Transactional
public class ProjectAccess {

    private final ProjectRepository projects;
    private final AuditLog auditLog;
    private final Clock clock;

    ProjectAccess(ProjectRepository projects, AuditLog auditLog, Clock clock) {
        this.projects = projects;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public void grant(ProjectId projectId, UUID userId, String actor) {
        if (projects.find(projectId).isEmpty()) {
            throw new ProjectNotFoundException(projectId);
        }
        projects.addMember(projectId, userId);
        auditLog.append(AuditEvent.of("MemberGranted", actor, "PROJECT", projectId.value(),
                JsonPayload.of("userId", userId.toString()), clock.instant()));
    }

    @Transactional(readOnly = true)
    public boolean isMember(ProjectId projectId, UUID userId) {
        return projects.isMember(projectId, userId);
    }

    /** Succeeds silently for a member; otherwise behaves as if the project did not exist. */
    @Transactional(readOnly = true)
    public void requireMember(ProjectId projectId, UUID userId) {
        if (!projects.isMember(projectId, userId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    @Transactional(readOnly = true)
    public List<Project> projectsOf(UUID userId) {
        return projects.projectsOf(userId);
    }
}
