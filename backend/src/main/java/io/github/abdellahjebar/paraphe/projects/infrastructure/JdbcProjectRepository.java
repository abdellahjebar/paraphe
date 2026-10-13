package io.github.abdellahjebar.paraphe.projects.infrastructure;

import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.projects.domain.WorkPackage;
import io.github.abdellahjebar.paraphe.projects.port.ProjectRepository;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProjectRepository implements ProjectRepository {

    private final JdbcClient jdbc;

    JdbcProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(Project project, List<OpinionStatus> statuses) {
        jdbc.sql("INSERT INTO project (id, name, created_at) VALUES (:id, :name, :createdAt)")
                .param("id", project.id().value())
                .param("name", project.name())
                .param("createdAt", OffsetDateTime.ofInstant(project.createdAt(), ZoneOffset.UTC))
                .update();
        statuses.forEach(status -> addStatus(project.id(), status));
    }

    @Override
    public Optional<Project> find(ProjectId id) {
        return jdbc.sql("SELECT id, name, created_at FROM project WHERE id = :id")
                .param("id", id.value())
                .query((rs, n) -> new Project(
                        new ProjectId(rs.getObject("id", UUID.class)),
                        rs.getString("name"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public boolean hasWorkPackage(ProjectId projectId, String code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM work_package WHERE project_id = :p AND code = :c)")
                .param("p", projectId.value())
                .param("c", code)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void addWorkPackage(ProjectId projectId, WorkPackage workPackage) {
        jdbc.sql("INSERT INTO work_package (project_id, code, label) VALUES (:p, :code, :label)")
                .param("p", projectId.value())
                .param("code", workPackage.code())
                .param("label", workPackage.label())
                .update();
    }

    @Override
    public List<WorkPackage> workPackages(ProjectId projectId) {
        return jdbc.sql("SELECT code, label FROM work_package WHERE project_id = :p ORDER BY code")
                .param("p", projectId.value())
                .query((rs, n) -> new WorkPackage(rs.getString("code"), rs.getString("label")))
                .list();
    }

    @Override
    public void addParticipant(Participant participant) {
        jdbc.sql("""
                INSERT INTO participant (id, project_id, name, role, email, phone, active)
                VALUES (:id, :project, :name, :role, :email, :phone, :active)
                """)
                .param("id", participant.id().value())
                .param("project", participant.projectId().value())
                .param("name", participant.name())
                .param("role", participant.role().name())
                .param("email", participant.email())
                .param("phone", participant.phone())
                .param("active", participant.active())
                .update();
    }

    @Override
    public Optional<Participant> findParticipant(ProjectId projectId, ReviewerId id) {
        return jdbc.sql("""
                SELECT id, project_id, name, role, email, phone, active
                FROM participant WHERE project_id = :p AND id = :id
                """)
                .param("p", projectId.value())
                .param("id", id.value())
                .query((rs, n) -> toParticipant(rs))
                .optional();
    }

    @Override
    public void deactivateParticipant(ProjectId projectId, ReviewerId id) {
        jdbc.sql("UPDATE participant SET active = FALSE WHERE project_id = :p AND id = :id")
                .param("p", projectId.value())
                .param("id", id.value())
                .update();
    }

    @Override
    public List<Participant> participants(ProjectId projectId) {
        return jdbc.sql("""
                SELECT id, project_id, name, role, email, phone, active
                FROM participant WHERE project_id = :p ORDER BY name, id
                """)
                .param("p", projectId.value())
                .query((rs, n) -> toParticipant(rs))
                .list();
    }

    @Override
    public boolean hasStatus(ProjectId projectId, String code) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM opinion_status_definition WHERE project_id = :p AND code = :c)
                """)
                .param("p", projectId.value())
                .param("c", code)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void addStatus(ProjectId projectId, OpinionStatus status) {
        jdbc.sql("""
                INSERT INTO opinion_status_definition (project_id, code, label, effect)
                VALUES (:p, :code, :label, :effect)
                """)
                .param("p", projectId.value())
                .param("code", status.code())
                .param("label", status.label())
                .param("effect", status.effect().name())
                .update();
    }

    @Override
    public void deactivateStatus(ProjectId projectId, String code) {
        jdbc.sql("UPDATE opinion_status_definition SET active = FALSE WHERE project_id = :p AND code = :c")
                .param("p", projectId.value())
                .param("c", code)
                .update();
    }

    @Override
    public List<OpinionStatus> activeStatuses(ProjectId projectId) {
        return jdbc.sql("""
                SELECT code, label, effect FROM opinion_status_definition
                WHERE project_id = :p AND active ORDER BY code
                """)
                .param("p", projectId.value())
                .query((rs, n) -> new OpinionStatus(
                        rs.getString("code"),
                        rs.getString("label"),
                        OpinionEffect.valueOf(rs.getString("effect"))))
                .list();
    }

    @Override
    public void setDefaultReviewers(ProjectId projectId, String workPackageCode, Set<ReviewerId> reviewers) {
        jdbc.sql("DELETE FROM default_reviewer WHERE project_id = :p AND work_package_code = :c")
                .param("p", projectId.value())
                .param("c", workPackageCode)
                .update();
        for (ReviewerId reviewer : reviewers) {
            jdbc.sql("""
                    INSERT INTO default_reviewer (project_id, work_package_code, participant_id)
                    VALUES (:p, :c, :r)
                    """)
                    .param("p", projectId.value())
                    .param("c", workPackageCode)
                    .param("r", reviewer.value())
                    .update();
        }
    }

    @Override
    public Set<ReviewerId> defaultReviewers(ProjectId projectId, String workPackageCode) {
        List<UUID> ids = jdbc.sql("""
                SELECT d.participant_id
                FROM default_reviewer d
                JOIN participant p ON p.id = d.participant_id
                WHERE d.project_id = :p AND d.work_package_code = :c AND p.active
                ORDER BY p.name, p.id
                """)
                .param("p", projectId.value())
                .param("c", workPackageCode)
                .query((rs, n) -> rs.getObject("participant_id", UUID.class))
                .list();
        Set<ReviewerId> reviewers = new LinkedHashSet<>();
        ids.forEach(id -> reviewers.add(new ReviewerId(id)));
        return reviewers;
    }

    private static Participant toParticipant(ResultSet rs) throws SQLException {
        return new Participant(
                new ReviewerId(rs.getObject("id", UUID.class)),
                new ProjectId(rs.getObject("project_id", UUID.class)),
                rs.getString("name"),
                ReviewerRole.valueOf(rs.getString("role")),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getBoolean("active"));
    }
}
