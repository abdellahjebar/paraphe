package io.github.abdellahjebar.paraphe.audit.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One fact that happened: who did what, to which thing, and when.
 *
 * <p>The payload is a JSON object kept as text, so the domain does not depend on a JSON library.
 */
public record AuditEvent(
        UUID id,
        String type,
        String actor,
        String targetType,
        UUID targetId,
        String payload,
        Instant occurredAt) {

    public AuditEvent {
        Objects.requireNonNull(id, "id");
        requireText(type, "type");
        requireText(actor, "actor");
        requireText(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        payload = payload == null || payload.isBlank() ? "{}" : payload;
    }

    public static AuditEvent of(String type, String actor, String targetType, UUID targetId,
                                String payload, Instant occurredAt) {
        return new AuditEvent(UUID.randomUUID(), type, actor, targetType, targetId, payload, occurredAt);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
