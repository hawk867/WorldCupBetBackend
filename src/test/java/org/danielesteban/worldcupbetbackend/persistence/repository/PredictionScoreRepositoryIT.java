package org.danielesteban.worldcupbetbackend.persistence.repository;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.domain.entity.PredictionScore;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration tests for {@link PredictionScoreRepository}.
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class PredictionScoreRepositoryIT extends AbstractRepositoryIT {

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(120_000);
    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private PredictionScoreRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("findByPredictionId returns the score when present")
    void findByPredictionIdReturnsPresent() {
        Prediction prediction = persistPrediction();
        PredictionScore score = persistScore(prediction, 6, 4, 2, 0);

        assertThat(repository.findByPredictionId(prediction.getId()))
                .get()
                .extracting(PredictionScore::getId)
                .isEqualTo(score.getId());
    }

    @Test
    @DisplayName("deleteByPredictionId removes the row")
    void deleteByPredictionIdRemovesRow() {
        Prediction prediction = persistPrediction();
        persistScore(prediction, 4, 4, 0, 0);

        repository.deleteByPredictionId(prediction.getId());
        em().flush();
        em().clear();

        assertThat(repository.findByPredictionId(prediction.getId())).isEmpty();
    }

    @Test
    @DisplayName("sumPointsByUserId aggregates scores across a user's predictions")
    void sumPointsByUserIdAggregates() {
        User user = persistUser();
        Prediction p1 = persistPredictionFor(user);
        Prediction p2 = persistPredictionFor(user);
        persistScore(p1, 4, 4, 0, 0);
        persistScore(p2, 2, 0, 2, 0);

        assertThat(repository.sumPointsByUserId(user.getId())).isEqualTo(6);
    }

    @Test
    @DisplayName("sumPointsByUserId returns 0 for a user with no scored predictions")
    void sumPointsByUserIdReturnsZeroWhenAbsent() {
        User user = persistUser();

        assertThat(repository.sumPointsByUserId(user.getId())).isZero();
    }

    // --- fixtures ------------------------------------------------------------

    private PredictionScore persistScore(Prediction p, int points, int exact, int winner, int pen) {
        PredictionScore s = PredictionScore.builder()
                .prediction(p)
                .points(points)
                .exactScorePoints(exact)
                .winnerPoints(winner)
                .penaltiesPoints(pen)
                .calculatedAt(Instant.parse("2026-06-12T20:00:00Z"))
                .build();
        em().persist(s);
        em().flush();
        return s;
    }

    private Prediction persistPrediction() {
        return persistPredictionFor(persistUser());
    }

    private Prediction persistPredictionFor(User user) {
        Match match = persistMatch();
        Prediction p = Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(1)
                .awayGoals(1)
                .build();
        em().persist(p);
        em().flush();
        return p;
    }

    private User persistUser() {
        User u = User.builder()
                .email("score-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("h")
                .fullName("Score User")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em().persist(u);
        em().flush();
        return u;
    }

    private Match persistMatch() {
        Team home = team("H");
        Team away = team("A");
        em().persist(home);
        em().persist(away);
        em().flush();
        Match m = Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.FINISHED)
                .homeGoals(2)
                .awayGoals(1)
                .build();
        em().persist(m);
        em().flush();
        return m;
    }

    private Team team(String label) {
        int externalId = EXTERNAL_ID_SEQ.incrementAndGet();
        return Team.builder().externalId(externalId).name(label + "-" + externalId).build();
    }

    private Stage groupStage() {
        return em()
                .createQuery("SELECT s FROM Stage s WHERE s.name = 'GROUP_STAGE'", Stage.class)
                .getSingleResult();
    }
}
