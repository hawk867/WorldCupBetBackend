package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link User}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds the user with the given email, if any.
     *
     * @param email the email address (case-sensitive, matches the DB value)
     * @return the matching user, or {@link Optional#empty()} if none exists
     */
    Optional<User> findByEmail(String email);

    /**
     * Reports whether a user with the given email already exists. Intended
     * for authentication and user-creation flows that need a fast uniqueness
     * check without loading the entity.
     */
    boolean existsByEmail(String email);
}
