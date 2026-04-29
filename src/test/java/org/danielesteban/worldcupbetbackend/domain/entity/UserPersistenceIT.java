package org.danielesteban.worldcupbetbackend.domain.entity;

import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.danielesteban.worldcupbetbackend.support.ConstraintAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests for {@link User}.
 * <p>
 * Covers:
 * <ul>
 *   <li>Normal persistence assigns an id and populates {@code createdAt} via
 *       Spring Data JPA auditing (Requirement 7.4).</li>
 *   <li>The unique constraint {@code uk_users_email} (Requirement 3.1).</li>
 *   <li>The database-level check constraint {@code chk_users_role} that
 *       restricts {@code role} to {@code USER} / {@code ADMIN} (Requirement
 *       6.8). The check is exercised via native SQL because JPA would otherwise
 *       reject the invalid enum name before the statement reaches the DB.</li>
 * </ul>
 */
class UserPersistenceIT extends AbstractRepositoryIT {

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("persisting a valid user assigns an id and populates createdAt")
    void persistingValidUserAssignsIdAndCreatedAt() {
        User user = User.builder()
                .email("alice@example.com")
                .passwordHash("hash")
                .fullName("Alice Example")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();

        User saved = em.persistFlushFind(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("two users with the same email are rejected by uk_users_email")
    void duplicateEmailIsRejected() {
        em.persistAndFlush(User.builder()
                .email("bob@example.com")
                .passwordHash("hash")
                .fullName("Bob Example")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build());

        User duplicate = User.builder()
                .email("bob@example.com")
                .passwordHash("otherhash")
                .fullName("Other Bob")
                .role(UserRole.ADMIN)
                .passwordChanged(false)
                .build();

        ConstraintAssertions.assertViolates("uk_users_email",
                () -> em.persistAndFlush(duplicate));
    }

    @Test
    @DisplayName("inserting a user with an invalid role is rejected by chk_users_role")
    void invalidRoleIsRejectedByCheckConstraint() {
        // JPA would refuse the invalid enum, so we bypass it with native SQL.
        jakarta.persistence.Query insert = em.getEntityManager().createNativeQuery("""
                INSERT INTO users (email, password_hash, full_name, role, password_changed)
                VALUES ('malory@example.com', 'h', 'Malory', 'ROOT', false)
                """);

        ConstraintAssertions.assertViolates("chk_users_role", insert::executeUpdate);
    }
}
