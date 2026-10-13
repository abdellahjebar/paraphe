package io.github.abdellahjebar.paraphe.projects.infrastructure.web;

import io.github.abdellahjebar.paraphe.shared.security.AuthenticatedUser;
import io.github.abdellahjebar.paraphe.projects.application.ProjectAccess;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.projects.domain.WorkPackage;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projects and what is configured on them. Every route that takes a project id is guarded: a caller
 * who is not a member receives "not found", the same as for a project that does not exist.
 */
@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private static final String GUARD = "@projectAccessGuard.isMember(#projectId, authentication)";

    private final ProjectService projects;
    private final ProjectAccess access;

    ProjectController(ProjectService projects, ProjectAccess access) {
        this.projects = projects;
        this.access = access;
    }

    // ---- requests ----------------------------------------------------------------------------

    record CreateProjectRequest(@NotBlank @Size(max = Project.MAX_NAME_LENGTH) String name) {
    }

    record AddWorkPackageRequest(@NotBlank @Size(max = 50) String code, @Size(max = 200) String label) {
    }

    record DefaultReviewersRequest(@NotNull Set<UUID> reviewerIds) {
    }

    record AddParticipantRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull ReviewerRole role,
            @Size(max = 100) String specialty,
            @Size(max = 254) String email,
            @Size(max = 50) String phone) {
    }

    record DefineStatusRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String label,
            @NotNull OpinionEffect effect) {
    }

    // ---- responses ---------------------------------------------------------------------------

    record ProjectSummary(UUID id, String name, Instant createdAt) {
        static ProjectSummary of(Project project) {
            return new ProjectSummary(project.id().value(), project.name(), project.createdAt());
        }
    }

    record WorkPackageView(String code, String label, Set<UUID> defaultReviewerIds) {
    }

    record ParticipantView(UUID id, String name, ReviewerRole role, String specialty, String email,
                           String phone, boolean active) {
        static ParticipantView of(Participant p) {
            return new ParticipantView(p.id().value(), p.name(), p.role(), p.specialty(), p.email(), p.phone(),
                    p.active());
        }
    }

    record StatusView(String code, String label, OpinionEffect effect) {
        static StatusView of(OpinionStatus s) {
            return new StatusView(s.code(), s.label(), s.effect());
        }
    }

    record ProjectOverview(UUID id, String name, Instant createdAt, List<WorkPackageView> workPackages,
                           List<ParticipantView> participants, List<StatusView> statuses) {
    }

    // ---- projects ----------------------------------------------------------------------------

    @GetMapping
    List<ProjectSummary> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return access.projectsOf(user.userId()).stream().map(ProjectSummary::of).toList();
    }

    @PostMapping
    ResponseEntity<ProjectSummary> create(@Valid @RequestBody CreateProjectRequest body,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        Project project = projects.createProjectFor(user.userId(), body.name(), actor(user));
        return ResponseEntity.created(URI.create("/api/projects/" + project.id().value()))
                .body(ProjectSummary.of(project));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize(GUARD)
    ProjectOverview overview(@PathVariable UUID projectId) {
        return overviewOf(new ProjectId(projectId));
    }

    // ---- work packages -----------------------------------------------------------------------

    @PostMapping("/{projectId}/work-packages")
    @PreAuthorize(GUARD)
    ResponseEntity<WorkPackageView> addWorkPackage(@PathVariable UUID projectId,
                                                   @Valid @RequestBody AddWorkPackageRequest body,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        WorkPackage created = projects.addWorkPackage(new ProjectId(projectId), body.code(), body.label(),
                actor(user));
        return ResponseEntity.status(201).body(new WorkPackageView(created.code(), created.label(), Set.of()));
    }

    @PutMapping("/{projectId}/work-packages/{code}/default-reviewers")
    @PreAuthorize(GUARD)
    WorkPackageView setDefaultReviewers(@PathVariable UUID projectId, @PathVariable String code,
                                        @Valid @RequestBody DefaultReviewersRequest body,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectId id = new ProjectId(projectId);
        Set<ReviewerId> reviewers = body.reviewerIds().stream().map(ReviewerId::new).collect(Collectors.toSet());
        projects.setDefaultReviewers(id, code, reviewers, actor(user));
        WorkPackage workPackage = projects.workPackages(id).stream()
                .filter(wp -> wp.code().equals(code)).findFirst().orElseThrow();
        return workPackageView(id, workPackage);
    }

    // ---- participants ------------------------------------------------------------------------

    @PostMapping("/{projectId}/participants")
    @PreAuthorize(GUARD)
    ResponseEntity<ParticipantView> addParticipant(@PathVariable UUID projectId,
                                                   @Valid @RequestBody AddParticipantRequest body,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        Participant created = projects.addParticipant(new ProjectId(projectId), body.name(), body.role(),
                body.specialty(), body.email(), body.phone(), actor(user));
        return ResponseEntity.status(201).body(ParticipantView.of(created));
    }

    @PostMapping("/{projectId}/participants/{participantId}/deactivate")
    @PreAuthorize(GUARD)
    ResponseEntity<Void> deactivateParticipant(@PathVariable UUID projectId, @PathVariable UUID participantId,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        projects.deactivateParticipant(new ProjectId(projectId), new ReviewerId(participantId), actor(user));
        return ResponseEntity.noContent().build();
    }

    // ---- statuses ----------------------------------------------------------------------------

    @PostMapping("/{projectId}/statuses")
    @PreAuthorize(GUARD)
    ResponseEntity<StatusView> defineStatus(@PathVariable UUID projectId,
                                            @Valid @RequestBody DefineStatusRequest body,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        OpinionStatus created = projects.defineStatus(new ProjectId(projectId), body.code(), body.label(),
                body.effect(), actor(user));
        return ResponseEntity.status(201).body(StatusView.of(created));
    }

    @PostMapping("/{projectId}/statuses/{code}/deactivate")
    @PreAuthorize(GUARD)
    ResponseEntity<Void> deactivateStatus(@PathVariable UUID projectId, @PathVariable String code,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        projects.deactivateStatus(new ProjectId(projectId), code, actor(user));
        return ResponseEntity.noContent().build();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private ProjectOverview overviewOf(ProjectId id) {
        Project project = projects.project(id);
        List<WorkPackageView> workPackages = projects.workPackages(id).stream()
                .map(wp -> workPackageView(id, wp)).toList();
        List<ParticipantView> participants = projects.participants(id).stream().map(ParticipantView::of).toList();
        List<StatusView> statuses = projects.statuses(id).stream().map(StatusView::of).toList();
        return new ProjectOverview(project.id().value(), project.name(), project.createdAt(), workPackages,
                participants, statuses);
    }

    private WorkPackageView workPackageView(ProjectId id, WorkPackage workPackage) {
        Set<UUID> defaults = projects.expectedReviewersFor(id, workPackage.code()).stream()
                .map(ReviewerId::value).collect(Collectors.toSet());
        return new WorkPackageView(workPackage.code(), workPackage.label(), defaults);
    }

    /** The journal says who did it: the signed-in user, never a free-text name. */
    private static String actor(AuthenticatedUser user) {
        return "user:" + user.userId();
    }
}
