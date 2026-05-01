package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link UserScore}.
 * <p>
 * The primary key of {@link UserScore} is the {@code user_id}, shared with
 * {@link org.danielesteban.worldcupbetbackend.domain.entity.User} via
 * {@code @MapsId}.
 */
public interface UserScoreRepository extends JpaRepository<UserScore, Long> {

    /**
     * Returns the full leaderboard, sorted by {@code totalPoints} descending
     * and then by {@code exactCount} descending to break ties. Backed by
     * {@code idx_user_scores_ranking} so the query does not require a
     * runtime sort.
     */
    List<UserScore> findAllByOrderByTotalPointsDescExactCountDesc();
}
