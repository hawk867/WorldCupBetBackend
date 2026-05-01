package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record of an administrative action.
 * <p>
 * Every row references the administrator {@link User} who performed the
 * action. The FK is {@code ON DELETE RESTRICT} so admins who already have
 * audit history cannot be deleted, preserving accountability over time.
 * <p>
 * The {@code details} column stores a free-form {@code JSONB} payload using
 * Hibernate 6+'s native JSON mapping
 * ({@code @JdbcTypeCode(SqlTypes.JSON)}); this keeps the table flexible
 * without committing to a schema for every audit action type. Two indexes
 * speed up the admin-log views:
 * <ul>
 *   <li>{@code idx_audit_log_entity} on {@code (entity, entity_id)} for
 *       "show me the history of this record".</li>
 *   <li>{@code idx_audit_log_created_at} on {@code (created_at DESC)} for
 *       "show me the most recent actions".</li>
 * </ul>
 * <p>
 * {@link #createdAt} is populated by Spring Data JPA auditing. This entity
 * does NOT extend {@code Auditable} because it has no {@code updated_at}
 * column (audit rows are append-only).
 */
@Entity
@Table(
        name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_log_entity",     columnList = "entity, entity_id"),
                @Index(name = "idx_audit_log_created_at", columnList = "created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "admin_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_audit_log_admin")
    )
    private User admin;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 64)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
