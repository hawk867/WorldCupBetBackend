package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.danielesteban.worldcupbetbackend.support.ConstraintAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests for {@link AuditLog}.
 * <p>
 * Covers:
 * <ul>
 *   <li>{@code createdAt} is populated automatically via Spring Data JPA
 *       auditing (Requirement 7.4).</li>
 *   <li>{@code details} round-trips as JSONB with nested structure preserved
 *       (Requirement 2.9).</li>
 *   <li>{@code admin_id} is non-null.</li>
 *   <li>{@code fk_audit_log_admin ON DELETE RESTRICT} blocks deleting a
 *       referenced admin (Requirement 4.7).</li>
 * </ul>
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class AuditLogPersistenceIT extends AbstractRepositoryIT {

    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("persisting an AuditLog populates id and createdAt via @CreatedDate")
    void persistingValidAuditLogPopulatesIdAndCreatedAt() {
        User admin = persistAdmin();
        AuditLog log = auditLogFor(admin, "MATCH_ADJUSTED", "Match", 42L,
                Map.of("reason", "referee error", "newHomeGoals", 3));

        em().persist(log);
        em().flush();

        assertThat(log.getId()).isNotNull();
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("JSONB details round-trip through a reload, preserving nested structure")
    void jsonbDetailsRoundTripPreservesNestedStructure() {
        User admin = persistAdmin();

        // Use LinkedHashMap so key insertion order is predictable; this also
        // verifies that nested maps and lists come back intact.
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("ip", "10.0.0.7");
        nested.put("user_agent", "curl/8.4");
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("reason", "password reset");
        original.put("ticket", 12345);
        original.put("metadata", nested);
        original.put("changes", List.of("password_hash", "password_changed"));

        AuditLog log = auditLogFor(admin, "USER_PASSWORD_RESET", "User", admin.getId(), original);
        em().persist(log);
        em().flush();
        Long id = log.getId();

        em().clear();
        AuditLog reloaded = em().find(AuditLog.class, id);

        assertThat(reloaded.getDetails())
                .containsEntry("reason", "password reset")
                .containsEntry("ticket", 12345)
                .containsEntry("changes", List.of("password_hash", "password_changed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> reloadedNested = (Map<String, Object>) reloaded.getDetails().get("metadata");
        assertThat(reloadedNested)
                .containsEntry("ip", "10.0.0.7")
                .containsEntry("user_agent", "curl/8.4");
    }

    @Test
    @DisplayName("an AuditLog without an admin is rejected by NOT NULL on admin_id")
    void nullAdminIsRejected() {
        AuditLog log = AuditLog.builder()
                .admin(null)
                .action("USER_CREATED")
                .entity("User")
                .entityId(1L)
                .details(Map.of())
                .build();

        // Either Hibernate rejects the null @ManyToOne before flushing (the
        // usual case with optional = false) or PostgreSQL rejects it via
        // NOT NULL on admin_id. Either outcome satisfies the invariant.
        ConstraintAssertions.assertViolates("admin",
                () -> { em().persist(log); em().flush(); });
    }

    @Test
    @DisplayName("an AuditLog with an unknown admin_id is rejected by fk_audit_log_admin")
    void unknownAdminIsRejectedByForeignKey() {
        Long unknownAdminId = 999_999_999L;

        // Bypass the ORM (which would refuse to persist a transient User) and
        // write the row directly with an FK that does not match any user row.
        jakarta.persistence.Query insert = em().createNativeQuery("""
                INSERT INTO audit_log (admin_id, action, entity, entity_id, details)
                VALUES (?1, 'BOGUS', 'Nothing', NULL, '{}'::jsonb)
                """).setParameter(1, unknownAdminId);

        ConstraintAssertions.assertViolates("fk_audit_log_admin", insert::executeUpdate);
    }

    @Test
    @DisplayName("deleting a User referenced as admin by an AuditLog is rejected by fk_audit_log_admin")
    void deletingReferencedAdminIsRejected() {
        User admin = persistAdmin();
        em().persist(auditLogFor(admin, "SEED", "System", null, Map.of()));
        em().flush();
        Long adminId = admin.getId();

        em().clear();

        ConstraintAssertions.assertViolates("fk_audit_log_admin", () -> {
            em().remove(em().find(User.class, adminId));
            em().flush();
        });
    }

    // --- fixtures ------------------------------------------------------------

    private AuditLog auditLogFor(User admin, String action, String entity,
                                  Long entityId, Map<String, Object> details) {
        return AuditLog.builder()
                .admin(admin)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .details(details)
                .build();
    }

    private User persistAdmin() {
        User admin = User.builder()
                .email("admin-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("hash")
                .fullName("Audit Admin")
                .role(UserRole.ADMIN)
                .passwordChanged(true)
                .build();
        em().persist(admin);
        em().flush();
        return admin;
    }
}
