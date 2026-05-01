package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Team}.
 */
public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * Finds the team whose football-data.org identifier equals {@code externalId}.
     * Used by the sync scheduler to upsert teams pulled from the external API.
     */
    Optional<Team> findByExternalId(Integer externalId);
}
