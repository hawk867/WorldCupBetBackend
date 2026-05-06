package org.danielesteban.worldcupbetbackend.web.dto;

import java.time.Instant;
import java.util.Map;

/** Entrada del log de auditoría. */
public record AuditLogResponse(
    Long id,
    String adminEmail,
    String action,
    String entity,
    Long entityId,
    Map<String, Object> details,
    Instant createdAt
) {}
