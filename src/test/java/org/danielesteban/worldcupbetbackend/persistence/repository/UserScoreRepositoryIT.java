package org.danielesteban.worldcupbetbackend.persistence.repository;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration tests for {@link UserScoreRepository}.
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class UserScoreRepositoryIT extends AbstractRepositoryIT {

    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private UserScoreRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("findAllByOrderByTotalPointsDescExactCountDesc sorts the leaderboard")
    void findAllByOrderByTotalPointsDescExactCountDescSorts() {
        UserScore bronze = persistScore("bronze", 5, 1, 3);
        UserScore silver = persistScore("silver", 8, 2, 4);
        UserScore goldA  = persistScore("goldA",  10, 3, 5);
        UserScore goldB  = persistScore("goldB",  10, 2, 5); // ties totalPoints, loses on exactCount

        List<UserScore> ranking = repository.findAllByOrderByTotalPointsDescExactCountDesc();

        List<Long> ids = ranking.stream().map(UserScore::getUserId).toList();
        assertThat(ids).containsSubsequence(
                goldA.getUserId(), goldB.getUserId(),
                silver.getUserId(), bronze.getUserId());
    }

    private UserScore persistScore(String label, int total, int exact, int winner) {
        User user = User.builder()
                .email(label + "-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("h")
                .fullName(label)
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em().persist(user);
        em().flush();

        UserScore s = UserScore.builder()
                .user(user)
                .totalPoints(total)
                .exactCount(exact)
                .winnerCount(winner)
                .build();
        em().persist(s);
        em().flush();
        return s;
    }
}
