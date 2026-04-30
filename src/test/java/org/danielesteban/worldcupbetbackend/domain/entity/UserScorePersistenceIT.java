package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.danielesteban.worldcupbetbackend.support.ConstraintAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests for {@link UserScore} and the cascade /
 * restrict semantics shared across the domain.
 * <p>
 * Covers:
 * <ul>
 *   <li>{@link UserScore} shares its primary key with {@link User} via
 *       {@code @MapsId}.</li>
 *   <li>Non-negativity of {@code totalPoints}, {@code exactCount},
 *       {@code winnerCount}.</li>
 *   <li>{@code User → UserScore} cascade delete (schema + ORM
 *       {@code orphanRemoval}).</li>
 *   <li>{@code User → Prediction} cascade delete.</li>
 *   <li>{@code Match → Prediction} cascade delete.</li>
 *   <li>{@code Prediction → PredictionScore} cascade delete.</li>
 *   <li>{@code Team → Match} and {@code Stage → Match} delete restrict.</li>
 * </ul>
 * <p>
 * The cascade assertions to {@code AuditLog} live in
 * {@code AuditLogPersistenceIT} (Task 9) because they require that entity.
 * <p>
 * These tests use the native {@link EntityManager} instead of
 * {@link TestEntityManager#persistAndFlush} so that every fixture entity
 * stays managed in the current persistence context (TestEntityManager's
 * shortcuts can return detached instances depending on the flow, which
 * makes child persists fail with TransientPropertyValueException).
 * <p>
 * {@code @SuppressWarnings("resource")}: the {@link EntityManager} returned
 * by {@link TestEntityManager#getEntityManager()} is a shared, Spring-managed
 * instance. Closing it with try-with-resources would break later calls,
 * and the framework already handles its lifecycle.
 */
@SuppressWarnings("resource")
class UserScorePersistenceIT extends AbstractRepositoryIT {

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(60_000);
    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    // --- @MapsId and basic persistence --------------------------------------

    @Test
    @DisplayName("UserScore.userId equals User.id after persisting via @MapsId")
    void userScoreSharesPrimaryKeyWithUser() {
        User user = persistUser();
        UserScore score = UserScore.builder()
                .user(user)
                .totalPoints(0)
                .exactCount(0)
                .winnerCount(0)
                .build();

        em().persist(score);
        em().flush();

        assertThat(score.getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("negative total_points is rejected by chk_user_scores_total_points")
    void negativeTotalPointsIsRejected() {
        UserScore invalid = newScore(persistUser(), -1, 0, 0);

        ConstraintAssertions.assertViolates("chk_user_scores_total_points",
                () -> { em().persist(invalid); em().flush(); });
    }

    @Test
    @DisplayName("negative exact_count is rejected by chk_user_scores_exact_count")
    void negativeExactCountIsRejected() {
        UserScore invalid = newScore(persistUser(), 0, -1, 0);

        ConstraintAssertions.assertViolates("chk_user_scores_exact_count",
                () -> { em().persist(invalid); em().flush(); });
    }

    @Test
    @DisplayName("negative winner_count is rejected by chk_user_scores_winner_count")
    void negativeWinnerCountIsRejected() {
        UserScore invalid = newScore(persistUser(), 0, 0, -1);

        ConstraintAssertions.assertViolates("chk_user_scores_winner_count",
                () -> { em().persist(invalid); em().flush(); });
    }

    // --- Cascade / restrict semantics ---------------------------------------

    @Test
    @DisplayName("deleting a User cascades to its UserScore")
    void deletingUserCascadesToUserScore() {
        User user = persistUser();
        UserScore score = newScore(user, 10, 1, 2);
        em().persist(score);
        em().flush();
        Long userId = user.getId();

        // Clear before remove so Hibernate does not hold references to the
        // child entities in memory and refuse the cascade; the DB-level
        // ON DELETE CASCADE handles the aggregate row.
        em().clear();
        em().remove(em().find(User.class, userId));
        em().flush();
        em().clear();

        assertThat(em().find(User.class, userId)).isNull();
        assertThat(em().find(UserScore.class, userId)).isNull();
    }

    @Test
    @DisplayName("deleting a User cascades to its Predictions")
    void deletingUserCascadesToPredictions() {
        User user = persistUser();
        Match match = persistMatch();
        Prediction prediction = Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(1)
                .awayGoals(0)
                .build();
        em().persist(prediction);
        em().flush();
        Long predictionId = prediction.getId();
        Long userId = user.getId();

        em().clear();
        em().remove(em().find(User.class, userId));
        em().flush();
        em().clear();

        assertThat(em().find(Prediction.class, predictionId)).isNull();
    }

    @Test
    @DisplayName("deleting a Match cascades to its Predictions")
    void deletingMatchCascadesToPredictions() {
        User user = persistUser();
        Match match = persistMatch();
        Prediction prediction = Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(1)
                .awayGoals(0)
                .build();
        em().persist(prediction);
        em().flush();
        Long predictionId = prediction.getId();
        Long matchId = match.getId();

        em().clear();
        em().remove(em().find(Match.class, matchId));
        em().flush();
        em().clear();

        assertThat(em().find(Prediction.class, predictionId)).isNull();
    }

    @Test
    @DisplayName("deleting a Prediction cascades to its PredictionScore")
    void deletingPredictionCascadesToPredictionScore() {
        Prediction prediction = Prediction.builder()
                .user(persistUser())
                .match(persistMatch())
                .homeGoals(2)
                .awayGoals(1)
                .build();
        em().persist(prediction);
        em().flush();

        PredictionScore score = PredictionScore.builder()
                .prediction(prediction)
                .points(4)
                .exactScorePoints(4)
                .winnerPoints(0)
                .penaltiesPoints(0)
                .calculatedAt(Instant.parse("2026-06-12T20:00:00Z"))
                .build();
        em().persist(score);
        em().flush();
        Long scoreId = score.getId();
        Long predictionId = prediction.getId();

        em().clear();
        em().remove(em().find(Prediction.class, predictionId));
        em().flush();
        em().clear();

        assertThat(em().find(PredictionScore.class, scoreId)).isNull();
    }

    @Test
    @DisplayName("deleting a Team referenced by a Match is rejected by fk_matches_home_team")
    void deletingReferencedHomeTeamIsRejected() {
        Match match = persistMatch();
        Long homeId = match.getHomeTeam().getId();

        em().clear();

        ConstraintAssertions.assertViolates("fk_matches_home_team", () -> {
            em().remove(em().find(Team.class, homeId));
            em().flush();
        });
    }

    @Test
    @DisplayName("deleting a Team referenced by a Match is rejected by fk_matches_away_team")
    void deletingReferencedAwayTeamIsRejected() {
        Match match = persistMatch();
        Long awayId = match.getAwayTeam().getId();

        em().clear();

        ConstraintAssertions.assertViolates("fk_matches_away_team", () -> {
            em().remove(em().find(Team.class, awayId));
            em().flush();
        });
    }

    @Test
    @DisplayName("deleting a Stage referenced by a Match is rejected by fk_matches_stage")
    void deletingReferencedStageIsRejected() {
        Match match = persistMatch();
        Long stageId = match.getStage().getId();

        em().clear();

        ConstraintAssertions.assertViolates("fk_matches_stage", () -> {
            em().remove(em().find(Stage.class, stageId));
            em().flush();
        });
    }

    // --- fixtures ------------------------------------------------------------

    private UserScore newScore(User user, int total, int exact, int winner) {
        return UserScore.builder()
                .user(user)
                .totalPoints(total)
                .exactCount(exact)
                .winnerCount(winner)
                .build();
    }

    private User persistUser() {
        User user = User.builder()
                .email("scoreuser-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("hash")
                .fullName("Score User")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em().persist(user);
        em().flush();
        return user;
    }

    private Match persistMatch() {
        Team home = team("Home");
        Team away = team("Away");
        em().persist(home);
        em().persist(away);
        em().flush();

        Match match = Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.SCHEDULED)
                .build();
        em().persist(match);
        em().flush();
        return match;
    }

    private Team team(String nameBase) {
        int externalId = EXTERNAL_ID_SEQ.incrementAndGet();
        return Team.builder()
                .externalId(externalId)
                .name(nameBase + "-" + externalId)
                .build();
    }

    private Stage groupStage() {
        return em()
                .createQuery("SELECT s FROM Stage s WHERE s.name = 'GROUP_STAGE'", Stage.class)
                .getSingleResult();
    }
}
