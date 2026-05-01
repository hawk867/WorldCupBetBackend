package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link AuditLog}.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Returns every audit entry written by an administrator, newest first.
     * Backs the "my recent admin actions" view.
     */
    List<AuditLog> findAllByAdminIdOrderByCreatedAtDesc(Long adminId);

    /**
     * Returns every audit entry targeting a specific domain row, newest
     * first. Backed by the composite index {@code idx_audit_log_entity} on
     * {@code (entity, entity_id)}.
     */
    List<AuditLog> findAllByEntityAndEntityIdOrderByCreatedAtDesc(String entity, Long entityId);
}
