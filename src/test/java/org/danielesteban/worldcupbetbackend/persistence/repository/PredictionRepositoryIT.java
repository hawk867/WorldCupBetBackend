package org.danielesteban.worldcupbetbackend.persistence.repository;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
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
 * Repository integration tests for {@link PredictionRepository}.
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class PredictionRepositoryIT extends AbstractRepositoryIT {

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(110_000);
    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private PredictionRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("findByUserIdAndMatchId returns the prediction when present")
    void findByUserIdAndMatchIdReturnsPresent() {
        User user = persistUser();
        Match match = persistMatch();
        Prediction prediction = persistPrediction(user, match, 1, 2);

        assertThat(repository.findByUserIdAndMatchId(user.getId(), match.getId()))
                .get()
                .extracting(Prediction::getId)
                .isEqualTo(prediction.getId());
    }

    @Test
    @DisplayName("findByUserIdAndMatchId returns empty when absent")
    void findByUserIdAndMatchIdReturnsEmptyWhenAbsent() {
        User user = persistUser();
        Match match = persistMatch();

        assertThat(repository.findByUserIdAndMatchId(user.getId(), match.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUserId returns every prediction the user owns")
    void findAllByUserIdReturnsOwned() {
        User user = persistUser();
        Match m1 = persistMatch();
        Match m2 = persistMatch();
        persistPrediction(user, m1, 1, 0);
        persistPrediction(user, m2, 2, 2);

        assertThat(repository.findAllByUserId(user.getId())).hasSize(2);
    }

    @Test
    @DisplayName("findAllByMatchId returns every prediction for the match")
    void findAllByMatchIdReturnsForMatch() {
        User u1 = persistUser();
        User u2 = persistUser();
        Match match = persistMatch();
        persistPrediction(u1, match, 1, 0);
        persistPrediction(u2, match, 2, 2);

        assertThat(repository.findAllByMatchId(match.getId())).hasSize(2);
    }

    @Test
    @DisplayName("existsByUserIdAndMatchId reflects presence")
    void existsByUserIdAndMatchIdReflectsPresence() {
        User user = persistUser();
        Match match = persistMatch();

        assertThat(repository.existsByUserIdAndMatchId(user.getId(), match.getId())).isFalse();

        persistPrediction(user, match, 3, 1);

        assertThat(repository.existsByUserIdAndMatchId(user.getId(), match.getId())).isTrue();
    }

    // --- fixtures ------------------------------------------------------------

    private Prediction persistPrediction(User user, Match match, int home, int away) {
        Prediction p = Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(home)
                .awayGoals(away)
                .build();
        em().persist(p);
        em().flush();
        return p;
    }

    private User persistUser() {
        User u = User.builder()
                .email("pred-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("h")
                .fullName("Pred User")
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
                .status(MatchStatus.SCHEDULED)
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
