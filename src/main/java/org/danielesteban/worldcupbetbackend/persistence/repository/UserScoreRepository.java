package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     * Returns the full leaderboard with user data eagerly fetched,
     * sorted by {@code totalPoints} descending and then by {@code exactCount}
     * descending to break ties.
     */
    @Query("SELECT us FROM UserScore us JOIN FETCH us.user ORDER BY us.totalPoints DESC, us.exactCount DESC")
    List<UserScore> findAllByOrderByTotalPointsDescExactCountDesc();
}
