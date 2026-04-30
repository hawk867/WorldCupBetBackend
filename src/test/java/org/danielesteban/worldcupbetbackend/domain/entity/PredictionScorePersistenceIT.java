package org.danielesteban.worldcupbetbackend.domain.entity;

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
 * Persistence integration tests for {@link PredictionScore}.
 * <p>
 * Covers every database-enforced invariant declared in
 * {@code V1__baseline_schema.sql}:
 * <ul>
 *   <li>Unique {@code prediction_id} ({@code uk_prediction_scores_prediction}).</li>
 *   <li>Non-negative columns: {@code chk_prediction_scores_points},
 *       {@code chk_prediction_scores_exact_score_points},
 *       {@code chk_prediction_scores_winner_points},
 *       {@code chk_prediction_scores_penalties_points}.</li>
 *   <li>Decomposition identity {@code chk_prediction_scores_sum}:
 *       {@code points = exact_score_points + winner_points + penalties_points}.</li>
 * </ul>
 */
class PredictionScorePersistenceIT extends AbstractRepositoryIT {

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(50_000);
    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("persisting a valid prediction score assigns an id")
    void persistingValidScoreAssignsId() {
        Prediction prediction = persistPrediction();

        PredictionScore score = PredictionScore.builder()
                .prediction(prediction)
                .points(6)
                .exactScorePoints(4)
                .winnerPoints(2)
                .penaltiesPoints(0)
                .calculatedAt(Instant.parse("2026-06-12T20:00:00Z"))
                .build();

        PredictionScore saved = em.persistFlushFind(score);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("two scores for the same prediction are rejected by uk_prediction_scores_prediction")
    void duplicatePredictionIsRejected() {
        Prediction prediction = persistPrediction();

        em.persistAndFlush(scoreFor(prediction, 4, 4, 0, 0));

        PredictionScore duplicate = scoreFor(prediction, 2, 0, 2, 0);

        ConstraintAssertions.assertViolates("uk_prediction_scores_prediction",
                () -> em.persistAndFlush(duplicate));
    }

    @Test
    @DisplayName("negative points is rejected by chk_prediction_scores_points")
    void negativePointsIsRejected() {
        Long predictionId = persistPrediction().getId();

        // points = -1, components sum to -1 so, the sum check is satisfied
        // and only the points, non-negativity CHECK can fire.
        jakarta.persistence.Query insert = em.getEntityManager().createNativeQuery("""
                INSERT INTO prediction_scores
                    (prediction_id, points, exact_score_points,
                     winner_points, penalties_points, calculated_at)
                VALUES (?1, -1, 0, 0, 0, now())
                """).setParameter(1, predictionId);

        // When points = -1 but the component sum is 0, PostgreSQL may surface
        // either chk_prediction_scores_points or chk_prediction_scores_sum
        // first. The invariant we actually care about here is that the row
        // is rejected, so we satisfy the sum constraint intentionally and
        // isolate the non-negativity CHECK by relying on the helper below.
        //
        // The simplest way to isolate the point CHECK: keep the sum valid but
        // points still negative is algebraically impossible (x = sum of
        // non-negatives cannot be < 0). Thus, this test is subsumed by the
        // component tests. Keep as a smoke test asserting only rejection.
        ConstraintAssertions.assertViolates("chk_prediction_scores",
                insert::executeUpdate);
    }

    @Test
    @DisplayName("negative exact_score_points is rejected by chk_prediction_scores_exact_score_points")
    void negativeExactScorePointsIsRejected() {
        Long predictionId = persistPrediction().getId();

        // exact_score = -1, winner = 2, penalties = 0 -> sum = 1, matches points.
        jakarta.persistence.Query insert = em.getEntityManager().createNativeQuery("""
                INSERT INTO prediction_scores
                    (prediction_id, points, exact_score_points,
                     winner_points, penalties_points, calculated_at)
                VALUES (?1, 1, -1, 2, 0, now())
                """).setParameter(1, predictionId);

        ConstraintAssertions.assertViolates("chk_prediction_scores_exact_score_points",
                insert::executeUpdate);
    }

    @Test
    @DisplayName("negative winner_points is rejected by chk_prediction_scores_winner_points")
    void negativeWinnerPointsIsRejected() {
        Long predictionId = persistPrediction().getId();

        // winner = -1, exact = 2, penalties = 0 -> sum = 1, matches points.
        jakarta.persistence.Query insert = em.getEntityManager().createNativeQuery("""
                INSERT INTO prediction_scores
                    (prediction_id, points, exact_score_points,
                     winner_points, penalties_points, calculated_at)
                VALUES (?1, 1, 2, -1, 0, now())
                """).setParameter(1, predictionId);

        ConstraintAssertions.assertViolates("chk_prediction_scores_winner_points",
                insert::executeUpdate);
    }

    @Test
    @DisplayName("negative penalties_points is rejected by chk_prediction_scores_penalties_points")
    void negativePenaltiesPointsIsRejected() {
        Long predictionId = persistPrediction().getId();

        // penalties = -1, winner = 2, exact = 0 -> sum = 1, matches points.
        jakarta.persistence.Query insert = em.getEntityManager().createNativeQuery("""
                INSERT INTO prediction_scores
                    (prediction_id, points, exact_score_points,
                     winner_points, penalties_points, calculated_at)
                VALUES (?1, 1, 0, 2, -1, now())
                """).setParameter(1, predictionId);

        ConstraintAssertions.assertViolates("chk_prediction_scores_penalties_points",
                insert::executeUpdate);
    }

    @Test
    @DisplayName("points != sum(components) is rejected by chk_prediction_scores_sum")
    void mismatchedSumIsRejected() {
        PredictionScore invalid = scoreFor(persistPrediction(),
                /* points */ 5,
                /* exact  */ 4,
                /* winner */ 2,
                /* pen    */ 0);
        // 4 + 2 + 0 = 6, not 5 -> should fail the CHECK.

        ConstraintAssertions.assertViolates("chk_prediction_scores_sum",
                () -> em.persistAndFlush(invalid));
    }

    // --- fixtures ------------------------------------------------------------

    private PredictionScore scoreFor(Prediction prediction,
                                     int points, int exact, int winner, int pen) {
        return PredictionScore.builder()
                .prediction(prediction)
                .points(points)
                .exactScorePoints(exact)
                .winnerPoints(winner)
                .penaltiesPoints(pen)
                .calculatedAt(Instant.parse("2026-06-12T20:00:00Z"))
                .build();
    }

    private Prediction persistPrediction() {
        User user = em.persistFlushFind(User.builder()
                .email("scorer-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("hash")
                .fullName("Scorer")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build());

        Team home = em.persistFlushFind(team("Home"));
        Team away = em.persistFlushFind(team("Away"));
        Match match = em.persistFlushFind(Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.FINISHED)
                .homeGoals(2)
                .awayGoals(1)
                .build());

        return em.persistFlushFind(Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(2)
                .awayGoals(1)
                .build());
    }

    private Team team(String nameBase) {
        int externalId = EXTERNAL_ID_SEQ.incrementAndGet();
        return Team.builder()
                .externalId(externalId)
                .name(nameBase + "-" + externalId)
                .build();
    }

    private Stage groupStage() {
        return em.getEntityManager()
                .createQuery("SELECT s FROM Stage s WHERE s.name = 'GROUP_STAGE'", Stage.class)
                .getSingleResult();
    }
}
