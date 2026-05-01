package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration tests for {@link StageRepository}.
 * <p>
 * These tests do not seed any fixtures because the seven tournament stages
 * are already loaded by {@code V2__reference_data_stages.sql} on every run.
 */
class StageRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private StageRepository repository;

    @Test
    @DisplayName("findByName returns the seeded stage")
    void findByNameReturnsSeededStage() {
        assertThat(repository.findByName("QUARTER_FINAL"))
                .get()
                .extracting(Stage::getOrderIdx)
                .isEqualTo(4);
    }

    @Test
    @DisplayName("findAllByOrderByOrderIdxAsc returns the 7 seed stages in order")
    void findAllByOrderReturnsSeededStagesOrdered() {
        List<Stage> stages = repository.findAllByOrderByOrderIdxAsc();

        assertThat(stages)
                .extracting(Stage::getName)
                .containsExactly(
                        "GROUP_STAGE", "ROUND_OF_32", "ROUND_OF_16",
                        "QUARTER_FINAL", "SEMI_FINAL", "THIRD_PLACE", "FINAL");
    }
}
