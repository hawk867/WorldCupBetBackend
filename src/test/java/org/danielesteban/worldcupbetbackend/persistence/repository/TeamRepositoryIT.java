package org.danielesteban.worldcupbetbackend.persistence.repository;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration tests for {@link TeamRepository}.
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class TeamRepositoryIT extends AbstractRepositoryIT {

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(90_000);

    @Autowired
    private TeamRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("findByExternalId returns the matching team when present")
    void findByExternalIdReturnsTeamWhenPresent() {
        Team team = persistTeam(EXTERNAL_ID_SEQ.incrementAndGet(), "Argentina");

        assertThat(repository.findByExternalId(team.getExternalId()))
                .get()
                .extracting(Team::getName)
                .isEqualTo("Argentina");
    }

    @Test
    @DisplayName("findByExternalId returns empty when no team matches")
    void findByExternalIdReturnsEmptyWhenAbsent() {
        assertThat(repository.findByExternalId(-1)).isEmpty();
    }

    private Team persistTeam(Integer externalId, String name) {
        Team t = Team.builder().externalId(externalId).name(name).build();
        em().persist(t);
        em().flush();
        return t;
    }
}
