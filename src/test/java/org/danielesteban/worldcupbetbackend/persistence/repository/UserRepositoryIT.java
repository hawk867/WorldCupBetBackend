package org.danielesteban.worldcupbetbackend.persistence.repository;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration tests for {@link UserRepository}.
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class UserRepositoryIT extends AbstractRepositoryIT {

    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private UserRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("findByEmail returns the matching user when present")
    void findByEmailReturnsUserWhenPresent() {
        User user = persistUser("alice");

        assertThat(repository.findByEmail(user.getEmail()))
                .get()
                .extracting(User::getId)
                .isEqualTo(user.getId());
    }

    @Test
    @DisplayName("findByEmail returns empty when no user matches")
    void findByEmailReturnsEmptyWhenAbsent() {
        assertThat(repository.findByEmail("nobody-" + EMAIL_SEQ.incrementAndGet() + "@example.com"))
                .isEmpty();
    }

    @Test
    @DisplayName("existsByEmail reflects actual presence")
    void existsByEmailReflectsPresence() {
        User user = persistUser("bob");

        assertThat(repository.existsByEmail(user.getEmail())).isTrue();
        assertThat(repository.existsByEmail("nobody-" + EMAIL_SEQ.incrementAndGet() + "@example.com")).isFalse();
    }

    private User persistUser(String label) {
        User u = User.builder()
                .email(label + "-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("hash")
                .fullName(label)
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em().persist(u);
        em().flush();
        return u;
    }
}
