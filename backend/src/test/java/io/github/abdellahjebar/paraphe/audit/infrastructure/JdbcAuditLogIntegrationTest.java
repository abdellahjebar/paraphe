package io.github.abdellahjebar.paraphe.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcAuditLogIntegrationTest {

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private JdbcClient jdbc;

    private AuditEvent appendOne(UUID targetId, String type, Instant at) {
        AuditEvent event = AuditEvent.of(type, "reviewer:control-office", "REVISION", targetId,
                "{\"status\":\"VAO\"}", at);
        auditLog.append(event);
        return event;
    }

    @Test
    void appendedEventCanBeReadBack() {
        UUID target = UUID.randomUUID();
        AuditEvent event = appendOne(target, "OpinionGiven", Instant.parse("2026-10-12T10:00:00Z"));

        assertThat(auditLog.findByTarget("REVISION", target))
                .singleElement()
                .satisfies(read -> {
                    assertThat(read.id()).isEqualTo(event.id());
                    assertThat(read.type()).isEqualTo("OpinionGiven");
                    assertThat(read.actor()).isEqualTo("reviewer:control-office");
                    assertThat(read.occurredAt()).isEqualTo(Instant.parse("2026-10-12T10:00:00Z"));
                    assertThat(read.payload()).contains("\"status\"").contains("VAO");
                });
    }

    @Test
    void eventsAreReturnedOldestFirstAndOnlyForTheirTarget() {
        UUID target = UUID.randomUUID();
        appendOne(target, "second", Instant.parse("2026-10-12T11:00:00Z"));
        appendOne(target, "first", Instant.parse("2026-10-12T10:00:00Z"));
        appendOne(UUID.randomUUID(), "other", Instant.parse("2026-10-12T09:00:00Z"));

        assertThat(auditLog.findByTarget("REVISION", target))
                .extracting(AuditEvent::type)
                .containsExactly("first", "second");
    }

    @Test
    void databaseRefusesToUpdateAnEvent() {
        UUID target = UUID.randomUUID();
        AuditEvent event = appendOne(target, "OpinionGiven", Instant.parse("2026-10-12T10:00:00Z"));

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_event SET actor = 'someone-else' WHERE id = :id")
                .param("id", event.id())
                .update())
                .hasMessageContaining("append-only");

        assertThat(auditLog.findByTarget("REVISION", target).get(0).actor())
                .isEqualTo("reviewer:control-office");
    }

    @Test
    void databaseRefusesToDeleteAnEvent() {
        UUID target = UUID.randomUUID();
        AuditEvent event = appendOne(target, "OpinionGiven", Instant.parse("2026-10-12T10:00:00Z"));

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_event WHERE id = :id")
                .param("id", event.id())
                .update())
                .hasMessageContaining("append-only");

        assertThat(auditLog.findByTarget("REVISION", target)).hasSize(1);
    }

    @Test
    void databaseRefusesToTruncateTheJournal() {
        appendOne(UUID.randomUUID(), "OpinionGiven", Instant.parse("2026-10-12T10:00:00Z"));

        assertThatThrownBy(() -> jdbc.sql("TRUNCATE TABLE audit_event").update())
                .hasMessageContaining("append-only");
    }

    @Test
    void blankActorIsRejectedBeforeReachingTheDatabase() {
        assertThatThrownBy(() -> AuditEvent.of("OpinionGiven", " ", "REVISION", UUID.randomUUID(),
                "{}", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
