package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.PredictionScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Spring Data repository for {@link PredictionScore}.
 */
public interface PredictionScoreRepository extends JpaRepository<PredictionScore, Long> {

    /**
     * Finds the score previously computed for a prediction, if any.
     */
    Optional<PredictionScore> findByPredictionId(Long predictionId);

    /**
     * Deletes the score for a prediction. Used by the scoring service when a
     * match is corrected and the previous score must be invalidated before
     * recomputing.
     */
    @Modifying
    @Transactional
    void deleteByPredictionId(Long predictionId);

    /**
     * Returns the sum of {@code points} across every prediction score owned
     * (transitively) by the given user, or {@code 0} if the user has no
     * scored predictions. Uses {@code COALESCE} so callers never have to
     * handle a {@code null} aggregate.
     */
    @Query("""
            SELECT COALESCE(SUM(ps.points), 0)
            FROM PredictionScore ps
            JOIN ps.prediction p
            WHERE p.user.id = :userId
            """)
    int sumPointsByUserId(@Param("userId") Long userId);
}
