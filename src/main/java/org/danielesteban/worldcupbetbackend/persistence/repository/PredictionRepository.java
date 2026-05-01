package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Prediction}.
 */
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /**
     * Finds a user's prediction for a given match, if any exists. Supports
     * the "edit my prediction before kickoff" flow.
     */
    Optional<Prediction> findByUserIdAndMatchId(Long userId, Long matchId);

    /**
     * Returns every prediction owned by a user. Used by the "my predictions"
     * history view.
     */
    List<Prediction> findAllByUserId(Long userId);

    /**
     * Returns every prediction made for a given match. Used by the scoring
     * sweep once the match reaches a score-bearing status.
     */
    List<Prediction> findAllByMatchId(Long matchId);

    /**
     * Reports whether the user has already submitted a prediction for this
     * match. Faster than loading the entity for the common "should I show
     * Create or Edit?" UI branch.
     */
    boolean existsByUserIdAndMatchId(Long userId, Long matchId);
}
