package io.github.abdellahjebar.paraphe.submission.infrastructure.web;

import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.submission.application.DashboardService;
import io.github.abdellahjebar.paraphe.submission.application.DashboardView;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The follow-up screen of a project. */
@RestController
@RequestMapping("/api/projects/{projectId}")
class DashboardController {

    private final DashboardService dashboard;

    DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    /**
     * @param workPackage restrict to one work package code
     * @param status restrict to one status: a revision status, {@code NOT_SENT} or {@code NO_REVISION}
     */
    @GetMapping("/dashboard")
    @PreAuthorize("@projectAccessGuard.isMember(#projectId, authentication)")
    DashboardView dashboard(@PathVariable UUID projectId,
                            @RequestParam(value = "workPackage", required = false) String workPackage,
                            @RequestParam(value = "status", required = false) String status) {
        return dashboard.dashboard(new ProjectId(projectId), workPackage, status);
    }
}
