package io.github.abdellahjebar.paraphe.audit.infrastructure;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit SQL on purpose: the journal is append-only, and an ORM that tracks changes
 * would only get in the way of proving that.
 */
@Repository
class JdbcAuditLog implements AuditLog {

    private final JdbcClient jdbc;

    JdbcAuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEvent event) {
        jdbc.sql("""
                INSERT INTO audit_event (id, event_type, actor, target_type, target_id, payload, occurred_at)
                VALUES (:id, :type, :actor, :targetType, :targetId, CAST(:payload AS jsonb), :occurredAt)
                """)
                .param("id", event.id())
                .param("type", event.type())
                .param("actor", event.actor())
                .param("targetType", event.targetType())
                .param("targetId", event.targetId())
                .param("payload", event.payload())
                .param("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<AuditEvent> findByTarget(String targetType, UUID targetId) {
        return jdbc.sql("""
                SELECT id, event_type, actor, target_type, target_id, payload::text AS payload, occurred_at
                FROM audit_event
                WHERE target_type = :targetType AND target_id = :targetId
                ORDER BY occurred_at, recorded_at
                """)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .query((rs, rowNum) -> new AuditEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("actor"),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getString("payload"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
