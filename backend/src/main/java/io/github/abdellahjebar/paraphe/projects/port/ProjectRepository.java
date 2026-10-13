package io.github.abdellahjebar.paraphe.projects.port;

import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.WorkPackage;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProjectRepository {

    /** Stores the project together with its starting statuses. */
    void create(Project project, List<OpinionStatus> statuses);

    Optional<Project> find(ProjectId id);

    boolean hasWorkPackage(ProjectId projectId, String code);

    void addWorkPackage(ProjectId projectId, WorkPackage workPackage);

    List<WorkPackage> workPackages(ProjectId projectId);

    void addParticipant(Participant participant);

    Optional<Participant> findParticipant(ProjectId projectId, ReviewerId id);

    void deactivateParticipant(ProjectId projectId, ReviewerId id);

    List<Participant> participants(ProjectId projectId);

    boolean hasStatus(ProjectId projectId, String code);

    void addStatus(ProjectId projectId, OpinionStatus status);

    void deactivateStatus(ProjectId projectId, String code);

    /** Statuses a reviewer may currently give, ordered by code. */
    List<OpinionStatus> activeStatuses(ProjectId projectId);

    /** Replaces the default reviewers of a work package. */
    void setDefaultReviewers(ProjectId projectId, String workPackageCode, Set<ReviewerId> reviewers);

    /** Default reviewers of a work package who are still active. */
    Set<ReviewerId> defaultReviewers(ProjectId projectId, String workPackageCode);
}
