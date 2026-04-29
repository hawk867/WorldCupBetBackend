package org.danielesteban.worldcupbetbackend.domain.entity;

import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.danielesteban.worldcupbetbackend.support.ConstraintAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests for {@link Stage}.
 * <p>
 * Covers the unique constraint {@code uk_stages_name}. The seven default
 * stages are seeded by {@code V2__reference_data_stages.sql}, so the test
 * data here uses distinct synthetic names that do not collide with seed
 * rows.
 */
class StagePersistenceIT extends AbstractRepositoryIT {

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("seeded stages from V2 are present at startup")
    void seededStagesArePresent() {
        Long groupStageRows = (Long) em.getEntityManager()
                .createQuery("SELECT COUNT(s) FROM Stage s WHERE s.name = 'GROUP_STAGE'")
                .getSingleResult();

        assertThat(groupStageRows).isEqualTo(1L);
    }

    @Test
    @DisplayName("persisting a synthetic stage assigns an id")
    void persistingValidStageAssignsId() {
        Stage stage = Stage.builder()
                .name("TEST_PLAYOFF")
                .orderIdx(99)
                .build();

        Stage saved = em.persistFlushFind(stage);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("two stages with the same name are rejected by uk_stages_name")
    void duplicateNameIsRejected() {
        em.persistAndFlush(Stage.builder().name("CUSTOM_STAGE").orderIdx(100).build());

        Stage duplicate = Stage.builder().name("CUSTOM_STAGE").orderIdx(101).build();

        ConstraintAssertions.assertViolates("uk_stages_name",
                () -> em.persistAndFlush(duplicate));
    }
}
