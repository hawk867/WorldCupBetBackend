package org.danielesteban.worldcupbetbackend.persistence.repository;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.entity.AuditLog;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration tests for {@link AuditLogRepository}.
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class AuditLogRepositoryIT extends AbstractRepositoryIT {

    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("findAllByAdminIdOrderByCreatedAtDesc returns the admin's entries newest-first")
    void findAllByAdminIdOrderByCreatedAtDescReturnsAdminHistory() throws InterruptedException {
        User admin = persistAdmin();
        AuditLog first  = persistAuditLog(admin, "ACTION_A", "User", 1L);
        Thread.sleep(Duration.ofMillis(5));
        AuditLog second = persistAuditLog(admin, "ACTION_B", "User", 2L);

        List<AuditLog> entries = repository.findAllByAdminIdOrderByCreatedAtDesc(admin.getId());

        List<Long> ids = entries.stream().map(AuditLog::getId).toList();
        assertThat(ids).containsSubsequence(second.getId(), first.getId());
    }

    @Test
    @DisplayName("findAllByEntityAndEntityIdOrderByCreatedAtDesc returns history of a target record")
    void findAllByEntityAndEntityIdReturnsRecordHistory() throws InterruptedException {
        User admin = persistAdmin();
        AuditLog older = persistAuditLog(admin, "EDITED", "Match", 77L);
        Thread.sleep(Duration.ofMillis(5));
        AuditLog newer = persistAuditLog(admin, "EDITED", "Match", 77L);
        persistAuditLog(admin, "EDITED", "Match", 78L); // different entity id, should not appear.

        List<AuditLog> entries = repository.findAllByEntityAndEntityIdOrderByCreatedAtDesc("Match", 77L);

        List<Long> ids = entries.stream().map(AuditLog::getId).toList();
        assertThat(ids).containsSubsequence(newer.getId(), older.getId());
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.getEntity()).isEqualTo("Match");
            assertThat(e.getEntityId()).isEqualTo(77L);
        });
    }

    // --- fixtures ------------------------------------------------------------

    private AuditLog persistAuditLog(User admin, String action, String entity, Long entityId) {
        AuditLog log = AuditLog.builder()
                .admin(admin)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .details(Map.of("note", action))
                .build();
        em().persist(log);
        em().flush();
        return log;
    }

    private User persistAdmin() {
        User admin = User.builder()
                .email("auditadmin-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("h")
                .fullName("Audit Admin")
                .role(UserRole.ADMIN)
                .passwordChanged(true)
                .build();
        em().persist(admin);
        em().flush();
        return admin;
    }
}
