package org.danielesteban.worldcupbetbackend.properties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.danielesteban.worldcupbetbackend.support.ConstraintAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Property-based tests for the non-negativity invariants declared in
 * {@code design.md § Correctness Properties}:
 * <ul>
 *   <li>Property 6 – Match goal non-negativity.</li>
 *   <li>Property 7 – Match penalty non-negativity.</li>
 *   <li>Property 10 – Prediction goal validity (non-negative, non-null).</li>
 *   <li>Property 14 – PredictionScore component non-negativity.</li>
 *   <li>Property 15 – UserScore counter non-negativity.</li>
 * </ul>
 * <p>
 * Each property runs {@value #TRIES} iterations with randomly generated
 * negative values. Every iteration executes in its own
 * {@code REQUIRES_NEW} transaction rolled back at the end so rejected
 * writes do not leak into subsequent iterations. See {@code UniquenessProperties}
 * for the rationale behind driving the property loop ourselves instead of
 * using jqwik's JUnit engine.
 * <p>
 * Tests that target individual component columns of {@code prediction_scores}
 * keep the sum decomposition balanced (adding the same negative offset to
 * one component and subtracting it from another summand is not possible
 * while respecting non-negativity, so these tests use native SQL to bypass
 * the sum CHECK and isolate the individual non-negativity CHECK).
 */
class NonNegativityPropertiesTest extends AbstractRepositoryIT {

    /** Minimum number of examples per property; matches the design requirement. */
    private static final int TRIES = 100;

    /** Fixed seed so failures are reproducible across runs. */
    private static final long RNG_SEED = 73;

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(300_000);
    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNew() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt;
    }

    // --- Property 6: Match goal non-negativity ------------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 6: Match goal non-negativity")
    @DisplayName("Property 6: any negative Match.homeGoals / awayGoals is rejected")
    void property6_matchGoalNonNegativity() {
        runProperty(rnd -> new int[] { negative(rnd), negative(rnd) }, goals -> {
            // Either column being negative should trigger its own CHECK.
            // We flip a coin to target home or away so both constraints
            // get exercised across the 100 iterations.
            boolean targetHome = (goals[0] & 1) == 0;
            Match match = validSchedulableMatch();
            if (targetHome) {
                match.setHomeGoals(goals[0]);
                ConstraintAssertions.assertViolates("chk_matches_home_goals",
                        () -> { em.persist(match); em.flush(); });
            } else {
                match.setAwayGoals(goals[1]);
                ConstraintAssertions.assertViolates("chk_matches_away_goals",
                        () -> { em.persist(match); em.flush(); });
            }
        });
    }

    // --- Property 7: Match penalty non-negativity ---------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 7: Match penalty non-negativity")
    @DisplayName("Property 7: any negative Match.homePenalties / awayPenalties is rejected")
    void property7_matchPenaltyNonNegativity() {
        runProperty(rnd -> new int[] { negative(rnd), negative(rnd) }, penalties -> {
            boolean targetHome = (penalties[0] & 1) == 0;
            Match match = validSchedulableMatch();
            // Set wentToPenalties=true so the consistency CHECK also requires
            // non-null values, but use a valid value on the non-target column
            // and the negative value on the target column.
            match.setWentToPenalties(true);
            if (targetHome) {
                match.setHomePenalties(penalties[0]);
                match.setAwayPenalties(3);
                ConstraintAssertions.assertViolates("chk_matches_home_penalties",
                        () -> { em.persist(match); em.flush(); });
            } else {
                match.setHomePenalties(3);
                match.setAwayPenalties(penalties[1]);
                ConstraintAssertions.assertViolates("chk_matches_away_penalties",
                        () -> { em.persist(match); em.flush(); });
            }
        });
    }

    // --- Property 10: Prediction goal validity ------------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 10: Prediction goal validity")
    @DisplayName("Property 10: any negative Prediction.homeGoals / awayGoals is rejected")
    void property10_predictionGoalNonNegativity() {
        runProperty(rnd -> new int[] { negative(rnd), negative(rnd) }, goals -> {
            boolean targetHome = (goals[0] & 1) == 0;
            User user = persistUser();
            Match match = persistMatch();
            Prediction prediction = Prediction.builder()
                    .user(user)
                    .match(match)
                    .homeGoals(targetHome ? goals[0] : 1)
                    .awayGoals(targetHome ? 1 : goals[1])
                    .build();

            String constraint = targetHome
                    ? "chk_predictions_home_goals"
                    : "chk_predictions_away_goals";

            ConstraintAssertions.assertViolates(constraint,
                    () -> { em.persist(prediction); em.flush(); });
        });
    }

    // --- Property 14: PredictionScore component non-negativity --------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 14: PredictionScore non-negativity")
    @DisplayName("Property 14: any negative component of PredictionScore is rejected")
    void property14_predictionScoreNonNegativity() {
        // Each iteration picks one of the four columns and inserts a row
        // where that column is negative. The other three columns are zero
        // so at least one CHECK must fire. Postgres reports the first
        // violated CHECK it evaluates, which may be the points check or
        // the individual component check depending on the row. The
        // invariant we care about is "the row is rejected by *some*
        // non-negativity CHECK on prediction_scores", so we assert the
        // common name prefix "chk_prediction_scores".
        runProperty(rnd -> new int[] { rnd.nextInt(4), negative(rnd) }, sample -> {
            int column = sample[0];
            int negative = sample[1];

            Prediction prediction = persistPrediction(persistUser(), persistMatch());
            Long predictionId = prediction.getId();

            int exact  = column == 0 ? negative : 0;
            int winner = column == 1 ? negative : 0;
            int pen    = column == 2 ? negative : 0;
            int points = column == 3 ? negative : exact + winner + pen;

            ConstraintAssertions.assertViolates("chk_prediction_scores", () ->
                    em.createNativeQuery("""
                            INSERT INTO prediction_scores
                                (prediction_id, points, exact_score_points,
                                 winner_points, penalties_points, calculated_at)
                            VALUES (?1, ?2, ?3, ?4, ?5, now())
                            """)
                            .setParameter(1, predictionId)
                            .setParameter(2, points)
                            .setParameter(3, exact)
                            .setParameter(4, winner)
                            .setParameter(5, pen)
                            .executeUpdate());
        });
    }

    // --- Property 15: UserScore counter non-negativity ----------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 15: UserScore non-negativity")
    @DisplayName("Property 15: any negative UserScore counter is rejected")
    void property15_userScoreNonNegativity() {
        runProperty(rnd -> new int[] { rnd.nextInt(3), negative(rnd) }, sample -> {
            int column = sample[0];
            int negative = sample[1];

            User user = persistUser();
            UserScore.UserScoreBuilder builder = UserScore.builder()
                    .user(user)
                    .totalPoints(1)
                    .exactCount(1)
                    .winnerCount(1);

            String constraint = switch (column) {
                case 0 -> {
                    builder.totalPoints(negative);
                    yield "chk_user_scores_total_points";
                }
                case 1 -> {
                    builder.exactCount(negative);
                    yield "chk_user_scores_exact_count";
                }
                case 2 -> {
                    builder.winnerCount(negative);
                    yield "chk_user_scores_winner_count";
                }
                default -> throw new IllegalStateException();
            };

            UserScore score = builder.build();
            ConstraintAssertions.assertViolates(constraint,
                    () -> { em.persist(score); em.flush(); });
        });
    }

    // --- property-runner ----------------------------------------------------

    private <T> void runProperty(Function<Random, T> generator, Consumer<T> assertion) {
        Random rnd = new Random(RNG_SEED);
        for (int i = 0; i < TRIES; i++) {
            T value = generator.apply(rnd);
            requiresNew().executeWithoutResult(status -> {
                try {
                    assertion.accept(value);
                } finally {
                    status.setRollbackOnly();
                }
            });
        }
    }

    /** Random negative integer in [-100_000, -1]. */
    private static int negative(Random rnd) {
        return -(rnd.nextInt(100_000) + 1);
    }

    // --- fixtures -----------------------------------------------------------

    private Match validSchedulableMatch() {
        Team home = team("H");
        Team away = team("A");
        em.persist(home);
        em.persist(away);
        em.flush();

        return Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.SCHEDULED)
                .build();
    }

    private Match persistMatch() {
        Match m = validSchedulableMatch();
        em.persist(m);
        em.flush();
        return m;
    }

    private User persistUser() {
        User u = User.builder()
                .email("nneg-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("h")
                .fullName("NonNeg User")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Prediction persistPrediction(User user, Match match) {
        Prediction p = Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(0)
                .awayGoals(0)
                .build();
        em.persist(p);
        em.flush();
        return p;
    }

    private Team team(String label) {
        int id = EXTERNAL_ID_SEQ.incrementAndGet();
        return Team.builder().externalId(id).name(label + "-" + id).build();
    }

    private Stage groupStage() {
        return em
                .createQuery("SELECT s FROM Stage s WHERE s.name = 'GROUP_STAGE'", Stage.class)
                .getSingleResult();
    }
}
