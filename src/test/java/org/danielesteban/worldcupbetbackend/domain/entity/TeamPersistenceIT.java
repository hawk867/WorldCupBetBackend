package org.danielesteban.worldcupbetbackend.domain.entity;

import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.danielesteban.worldcupbetbackend.support.ConstraintAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests for {@link Team}.
 * <p>
 * Covers the unique constraint {@code uk_teams_external_id}: two teams with
 * the same external id cannot coexist.
 */
class TeamPersistenceIT extends AbstractRepositoryIT {

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("persisting a valid team assigns an id")
    void persistingValidTeamAssignsId() {
        Team team = Team.builder()
                .externalId(101)
                .name("Argentina")
                .code("ARG")
                .flagUrl("https://flags.example/arg.png")
                .build();

        Team saved = em.persistFlushFind(team);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getExternalId()).isEqualTo(101);
    }

    @Test
    @DisplayName("two teams with the same external_id are rejected by uk_teams_external_id")
    void duplicateExternalIdIsRejected() {
        em.persistAndFlush(Team.builder().externalId(202).name("Brazil").build());

        Team duplicate = Team.builder().externalId(202).name("Not Brazil").build();

        ConstraintAssertions.assertViolates("uk_teams_external_id",
                () -> em.persistAndFlush(duplicate));
    }
}
