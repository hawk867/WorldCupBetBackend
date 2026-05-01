package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Stage}.
 */
public interface StageRepository extends JpaRepository<Stage, Long> {

    /**
     * Finds the stage with the given canonical name (e.g. {@code "GROUP_STAGE"}).
     */
    Optional<Stage> findByName(String name);

    /**
     * Returns every stage ordered ascending by {@code orderIdx}. This drives
     * the tournament-overview UI so stages appear in their chronological
     * sequence without client-side sorting.
     */
    List<Stage> findAllByOrderByOrderIdxAsc();
}
