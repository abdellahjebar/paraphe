package io.github.abdellahjebar.paraphe.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewService;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import io.github.abdellahjebar.paraphe.support.OwnerJdbc;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The application connects as a role that owns nothing. These tests play the attacker: someone who
 * can run SQL with the application's own credentials and wants to rewrite history.
 *
 * <p>There are two independent locks. The role has no privilege to change the evidence, and, were it
 * ever given one, the triggers would still refuse. The owner (migrations) is the only one who
 * could touch the triggers, and the application is not the owner.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseRolesIntegrationTest {

    /** Privileges the application role holds, table by table. Anything else must be refused. */
    private static final Map<String, Set<String>> TABLE_PRIVILEGES = Map.ofEntries(
            Map.entry("audit_event", Set.of("SELECT", "INSERT")),
            Map.entry("opinion", Set.of("SELECT", "INSERT")),
            Map.entry("revision", Set.of("SELECT", "INSERT")),
            Map.entry("review_link", Set.of("SELECT", "INSERT")),
            Map.entry("review_request", Set.of("SELECT", "INSERT")),
            Map.entry("project", Set.of("SELECT", "INSERT")),
            Map.entry("work_package", Set.of("SELECT", "INSERT")),
            Map.entry("document", Set.of("SELECT", "INSERT")),
            Map.entry("project_member", Set.of("SELECT", "INSERT")),
            Map.entry("participant", Set.of("SELECT", "INSERT")),
            Map.entry("opinion_status_definition", Set.of("SELECT", "INSERT")),
            Map.entry("default_reviewer", Set.of("SELECT", "INSERT", "DELETE")),
            Map.entry("app_user", Set.of("SELECT", "INSERT")));

    /** The only columns the application role may update, per table. */
    private static final Map<String, Set<String>> UPDATABLE_COLUMNS = Map.of(
            "review_link", Set.of("first_opened_at", "revoked_at"),
            "participant", Set.of("active"),
            "opinion_status_definition", Set.of("active"),
            "app_user", Set.of("password_hash", "must_change_password", "last_login_at", "active"),
            "project", Set.of("chase_from_days", "late_from_days"));

    private static final List<String> EVIDENCE = List.of("audit_event", "opinion", "revision", "review_link");

    private static final List<String> ALL_PRIVILEGES =
            List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER");

    @Autowired
    private JdbcClient app;

    @Autowired
    private OwnerJdbc ownerJdbc;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private ReviewService reviews;

    private JdbcClient owner() {
        return ownerJdbc.client();
    }

    // ---- who the application is ----------------------------------------------------------------

    @Test
    void theApplicationConnectsAsTheRestrictedRole() {
        assertThat(app.sql("SELECT current_user").query(String.class).single())
                .isEqualTo(TestcontainersConfiguration.APP_ROLE);
    }

    @Test
    void theRestrictedRoleHasNoSpecialPowers() {
        var flags = app.sql("""
                SELECT rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls
                FROM pg_roles WHERE rolname = current_user
                """)
                .query((rs, n) -> List.of(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3),
                        rs.getBoolean(4), rs.getBoolean(5)))
                .single();

        assertThat(flags).containsOnly(false);
    }

    @Test
    void theRestrictedRoleOwnsNoTable() {
        Integer owned = app.sql("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public' AND tableowner = current_user
                """).query(Integer.class).single();

        assertThat(owned).isZero();
    }

    // ---- the exact list of what it may do --------------------------------------------------------

    @Test
    void everyTableHasAnExplicitDecision() {
        List<String> tables = owner().sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """).query(String.class).list();

        // A new table without an entry here has no decision about who may touch it. Decide, in a
        // migration and in this test, rather than discovering it later.
        assertThat(tables).containsExactlyInAnyOrderElementsOf(TABLE_PRIVILEGES.keySet());
    }

    @Test
    void theRestrictedRoleHoldsExactlyTheTablePrivilegesListed() {
        TABLE_PRIVILEGES.forEach((table, expected) -> {
            Set<String> actual = new TreeSet<>();
            for (String privilege : ALL_PRIVILEGES) {
                Boolean held = owner().sql("SELECT has_table_privilege(:role, :table, :privilege)")
                        .param("role", TestcontainersConfiguration.APP_ROLE)
                        .param("table", "public." + table)
                        .param("privilege", privilege)
                        .query(Boolean.class).single();
                if (Boolean.TRUE.equals(held)) {
                    actual.add(privilege);
                }
            }
            assertThat(actual).as("table-level privileges on %s", table)
                    .containsExactlyInAnyOrderElementsOf(expected);
        });
    }

    @Test
    void theRestrictedRoleCanUpdateExactlyTheColumnsListed() {
        TABLE_PRIVILEGES.keySet().forEach(table -> {
            List<String> columns = owner().sql("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = :table
                    """).param("table", table).query(String.class).list();
            Set<String> updatable = new TreeSet<>();
            for (String column : columns) {
                Boolean held = owner().sql("SELECT has_column_privilege(:role, :table, :column, 'UPDATE')")
                        .param("role", TestcontainersConfiguration.APP_ROLE)
                        .param("table", "public." + table)
                        .param("column", column)
                        .query(Boolean.class).single();
                if (Boolean.TRUE.equals(held)) {
                    updatable.add(column);
                }
            }
            assertThat(updatable).as("updatable columns of %s", table)
                    .containsExactlyInAnyOrderElementsOf(UPDATABLE_COLUMNS.getOrDefault(table, Set.of()));
        });
    }

    @Test
    void itCannotEvenReadTheMigrationHistory() {
        assertThatThrownBy(() -> app.sql("SELECT * FROM flyway_schema_history").query().listOfRows())
                .rootCause().hasMessageContaining("permission denied");
    }

    // ---- the attacks -----------------------------------------------------------------------------

    @Test
    void itCannotDropATrigger() {
        assertThatThrownBy(() -> app.sql("DROP TRIGGER opinion_no_update_delete ON opinion").update())
                .rootCause().hasMessageContaining("must be owner");
    }

    @Test
    void itCannotDisableTriggers() {
        for (String table : EVIDENCE) {
            assertThatThrownBy(() -> app.sql("ALTER TABLE " + table + " DISABLE TRIGGER ALL").update())
                    .as(table).rootCause().hasMessageContaining("must be owner");
        }
    }

    @Test
    void itCannotBypassTriggersByClaimingToBeAReplica() {
        // session_replication_role = replica is the classic way to switch triggers off for a session.
        assertThatThrownBy(() -> app.sql("SET session_replication_role = replica").update())
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void itCannotUpdateDeleteOrTruncateTheEvidence() {
        for (String table : EVIDENCE) {
            assertThatThrownBy(() -> app.sql("DELETE FROM " + table).update())
                    .as("DELETE on %s", table).rootCause().hasMessageContaining("permission denied");
            assertThatThrownBy(() -> app.sql("TRUNCATE TABLE " + table).update())
                    .as("TRUNCATE on %s", table).rootCause().hasMessageContaining("permission denied");
        }
        assertThatThrownBy(() -> app.sql("UPDATE audit_event SET actor = 'someone-else'").update())
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.sql("UPDATE opinion SET effect = 'APPROVING'").update())
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.sql("UPDATE revision SET file_sha256 = repeat('0', 64)").update())
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void theRefusalComesFromThePrivilegeBeforeTheTriggerIsEvenReached() {
        assertThatThrownBy(() -> app.sql("UPDATE opinion SET comment = 'edited'").update())
                .rootCause().hasMessageContaining("permission denied")
                .hasMessageNotContaining("append-only");
    }

    @Test
    void itCannotRewriteALinksIdentityOrAnAccountsEmail() {
        assertThatThrownBy(() -> app.sql("UPDATE review_link SET token_hash = repeat('a', 64)").update())
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.sql("UPDATE review_link SET expires_at = expires_at + interval '1 year'")
                .update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.sql("UPDATE app_user SET email = 'hijack@example.org'").update())
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void itCannotChangeTheSchema() {
        assertThatThrownBy(() -> app.sql("CREATE TABLE evil (x integer)").update())
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.sql("ALTER TABLE opinion ADD COLUMN extra text").update())
                .rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> app.sql("DROP TABLE opinion").update())
                .rootCause().hasMessageContaining("must be owner");
    }

    @Test
    void itCannotCreateRolesOrGrantItselfAnything() {
        assertThatThrownBy(() -> app.sql("CREATE ROLE intruder LOGIN").update())
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void tryingToGrantItselfAPrivilegeGrantsNothing() {
        // PostgreSQL answers a GRANT by a role without the right to give it with a warning, not an
        // error, and grants nothing. What matters is the outcome, so that is what is checked.
        app.sql("GRANT UPDATE, DELETE, TRUNCATE ON opinion TO paraphe_app").update();

        for (String privilege : List.of("UPDATE", "DELETE", "TRUNCATE")) {
            Boolean held = owner().sql("SELECT has_table_privilege(:role, 'public.opinion', :privilege)")
                    .param("role", TestcontainersConfiguration.APP_ROLE)
                    .param("privilege", privilege)
                    .query(Boolean.class).single();
            assertThat(held).as(privilege).isFalse();
        }
    }

    // ---- it can still do its job ------------------------------------------------------------------

    @Test
    void itCanStillAppendEvidenceAndReadItBack() {
        UUID id = UUID.randomUUID();

        app.sql("""
                INSERT INTO audit_event (id, event_type, actor, target_type, target_id, occurred_at)
                VALUES (:id, 'RoleCheck', 'test', 'TEST', :target, now())
                """).param("id", id).param("target", UUID.randomUUID()).update();

        assertThat(app.sql("SELECT count(*) FROM audit_event WHERE id = :id").param("id", id)
                .query(Integer.class).single()).isEqualTo(1);
    }

    // ---- the second lock ---------------------------------------------------------------------------

    @Test
    void evenTheOwnerIsStoppedByTheTriggersOnceARowExists() {
        Project project = fixtures.project();
        ReviewerId control = fixtures.participant(project.id(), ReviewerRole.CONTROL_OFFICE).id();
        RevisionId revision = fixtures.revision(project.id()).id();
        reviews.open(revision, Set.of(control), "test");
        reviews.recordOpinion(revision, control,
                new OpinionStatus("VSO", "Visé sans observation", OpinionEffect.APPROVING), "ok");

        assertThatThrownBy(() -> owner().sql("UPDATE opinion SET effect = 'BLOCKING' WHERE revision_id = :r")
                .param("r", revision.value()).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner().sql("DELETE FROM opinion WHERE revision_id = :r")
                .param("r", revision.value()).update())
                .hasMessageContaining("append-only");
    }
}
