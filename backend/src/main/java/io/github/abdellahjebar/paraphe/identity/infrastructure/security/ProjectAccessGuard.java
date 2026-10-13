package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import io.github.abdellahjebar.paraphe.projects.application.ProjectAccess;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Used from {@code @PreAuthorize} on every endpoint that takes a project id:
 * {@code @PreAuthorize("@projectAccessGuard.isMember(#projectId, authentication)")}.
 *
 * <p>It only answers yes or no. The denial is turned into "not found" at the API boundary, so a
 * project the caller cannot use looks exactly like a project that does not exist.
 */
@Component("projectAccessGuard")
public class ProjectAccessGuard {

    private final ProjectAccess access;

    ProjectAccessGuard(ProjectAccess access) {
        this.access = access;
    }

    public boolean isMember(UUID projectId, Authentication authentication) {
        if (projectId == null || authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return access.isMember(new ProjectId(projectId), user.userId());
    }
}
