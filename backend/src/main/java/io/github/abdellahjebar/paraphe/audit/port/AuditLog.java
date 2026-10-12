package io.github.abdellahjebar.paraphe.audit.port;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import java.util.List;
import java.util.UUID;

/** The journal of events. It can only grow: there is deliberately no update or delete. */
public interface AuditLog {

    void append(AuditEvent event);

    /** Events about one target, oldest first. */
    List<AuditEvent> findByTarget(String targetType, UUID targetId);
}
