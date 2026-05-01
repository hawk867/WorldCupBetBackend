package org.danielesteban.worldcupbetbackend.properties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.domain.entity.PredictionScore;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
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
 * Property-based tests for the six uniqueness invariants declared in
 * {@code design.md § Correctness Properties}.
 * <p>
 * Each property runs {@value #TRIES} iterations with randomly generated
 * input. Every iteration executes in its own {@code REQUIRES_NEW} transaction
 * that is rolled back at the end, so collisions between iterations are
 * impossible and each iteration truly tests only what the property states.
 * <p>
 * We drive the property loop ourselves with a seeded {@link Random}
 * generator. This keeps the properties runnable by the standard JUnit
 * Jupiter engine (which we already use for {@code @DataJpaTest}), while
 * still providing the core PBT value: hundreds of distinct random inputs
 * per property, deterministic replay via a fixed seed, and counter-examples
 * surfaced as assertion failures. The jqwik dependency is kept on the
 * classpath for future properties that need shrinking or more sophisticated
 * generators.
 * <p>
 * Each {@code @Test} carries the tag specified by the design so the property
 * suite can be filtered or grouped in CI reports.
 */
class UniquenessPropertiesTest extends AbstractRepositoryIT {

    /** Minimum number of examples per property; matches the design requirement. */
    private static final int TRIES = 100;

    /** Fixed seed so failures are reproducible across runs. */
    private static final long RNG_SEED = 42;

    /**
     * Global pools of unique identifiers used to seed the generators. Shared
     * across tests because each iteration rolls back; values here exist only
     * to make sure separate iterations cannot accidentally collide.
     */
    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(200_000);
    private static final AtomicLong EMAIL_SEQ = new AtomicLong();
    private static final AtomicInteger STAGE_NAME_SEQ = new AtomicInteger();

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNew() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt;
    }

    // --- Property 1: User email uniqueness ----------------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 1: User email uniqueness")
    @DisplayName("Property 1: any two users with the same email collide on uk_users_email")
    void property1_userEmailUniqueness() {
        runProperty(rnd -> randomAlpha(rnd, 3, 20)
                        + "-" + EMAIL_SEQ.incrementAndGet() + "@example.com",
                email -> {
                    persistUserWith(email);
                    ConstraintAssertions.assertViolates("uk_users_email",
                            () -> persistUserWith(email));
                });
    }

    // --- Property 2: Team external_id uniqueness ----------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 2: Team external_id uniqueness")
    @DisplayName("Property 2: any two teams with the same external_id collide on uk_teams_external_id")
    void property2_teamExternalIdUniqueness() {
        runProperty(rnd -> EXTERNAL_ID_SEQ.incrementAndGet(),
                externalId -> {
                    persistTeamWith(externalId);
                    ConstraintAssertions.assertViolates("uk_teams_external_id",
                            () -> persistTeamWith(externalId));
                });
    }

    // --- Property 3: Match external_id uniqueness ---------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 3: Match external_id uniqueness")
    @DisplayName("Property 3: any two matches with the same external_id collide on uk_matches_external_id")
    void property3_matchExternalIdUniqueness() {
        runProperty(rnd -> EXTERNAL_ID_SEQ.incrementAndGet(),
                externalId -> {
                    persistMatchWith(externalId);
                    ConstraintAssertions.assertViolates("uk_matches_external_id",
                            () -> persistMatchWith(externalId));
                });
    }

    // --- Property 4: Stage name uniqueness ----------------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 4: Stage name uniqueness")
    @DisplayName("Property 4: any two stages with the same name collide on uk_stages_name")
    void property4_stageNameUniqueness() {
        runProperty(rnd -> "STG_" + randomAlpha(rnd, 3, 20)
                        + "_" + STAGE_NAME_SEQ.incrementAndGet(),
                name -> {
                    persistStageWith(name, 100);
                    ConstraintAssertions.assertViolates("uk_stages_name",
                            () -> persistStageWith(name, 200));
                });
    }

    // --- Property 9: Prediction uniqueness per (user, match) ----------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 9: Prediction uniqueness per (user, match)")
    @DisplayName("Property 9: two predictions for the same (user, match) collide on uk_predictions_user_match")
    void property9_predictionUniqueness() {
        runProperty(rnd -> new int[] { rnd.nextInt(11), rnd.nextInt(11) },
                score -> {
                    User user = persistUser();
                    Match match = persistMatch();
                    persistPrediction(user, match, score[0], score[1]);
                    ConstraintAssertions.assertViolates("uk_predictions_user_match",
                            () -> persistPrediction(user, match,
                                    (score[0] + 1) % 11, (score[1] + 1) % 11));
                });
    }

    // --- Property 12: PredictionScore uniqueness per prediction -------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 12: PredictionScore uniqueness per prediction")
    @DisplayName("Property 12: two scores for the same prediction collide on uk_prediction_scores_prediction")
    void property12_predictionScoreUniqueness() {
        runProperty(rnd -> {
                    // Valid decomposition so the sum CHECK cannot mask the
                    // uniqueness CHECK. points = exact + winner + pen.
                    int exact = rnd.nextInt(5);
                    int winner = rnd.nextInt(5);
                    int pen = rnd.nextInt(5);
                    return new int[] { exact + winner + pen, exact, winner, pen };
                },
                d -> {
                    Prediction prediction = persistPrediction(persistUser(), persistMatch(), 0, 0);
                    persistPredictionScore(prediction, d[0], d[1], d[2], d[3]);
                    ConstraintAssertions.assertViolates("uk_prediction_scores_prediction",
                            () -> persistPredictionScore(prediction, d[0], d[1], d[2], d[3]));
                });
    }

    // --- property-runner ----------------------------------------------------

    /**
     * Executes the given assertion for {@value #TRIES} random values drawn
     * from {@code generator}. Each iteration runs in its own rolled-back
     * transaction, so writes (including those that fail on a constraint) do
     * not leak between iterations.
     */
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

    private static String randomAlpha(Random rnd, int minLength, int maxLength) {
        int length = minLength + rnd.nextInt(maxLength - minLength + 1);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + rnd.nextInt(26)));
        }
        return sb.toString();
    }

    // --- fixtures -----------------------------------------------------------

    private void persistUserWith(String email) {
        User u = User.builder()
                .email(email)
                .passwordHash("h")
                .fullName("Prop User")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em.persist(u);
        em.flush();
    }

    private void persistTeamWith(Integer externalId) {
        Team t = Team.builder()
                .externalId(externalId)
                .name("Team-" + externalId)
                .build();
        em.persist(t);
        em.flush();
    }

    private void persistStageWith(String name, int orderIdx) {
        Stage s = Stage.builder()
                .name(name)
                .orderIdx(orderIdx)
                .build();
        em.persist(s);
        em.flush();
    }

    private void persistMatchWith(Integer externalId) {
        Team home = team("H");
        Team away = team("A");
        em.persist(home);
        em.persist(away);
        em.flush();

        Match m = Match.builder()
                .externalId(externalId)
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.SCHEDULED)
                .build();
        em.persist(m);
        em.flush();
    }

    private User persistUser() {
        User u = User.builder()
                .email("pbt-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("h")
                .fullName("PBT User")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Match persistMatch() {
        Team home = team("H");
        Team away = team("A");
        em.persist(home);
        em.persist(away);
        em.flush();

        Match m = Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.FINISHED)
                .homeGoals(1)
                .awayGoals(1)
                .build();
        em.persist(m);
        em.flush();
        return m;
    }

    private Prediction persistPrediction(User user, Match match, int home, int away) {
        Prediction p = Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(home)
                .awayGoals(away)
                .build();
        em.persist(p);
        em.flush();
        return p;
    }

    private void persistPredictionScore(Prediction prediction,
                                         int points, int exact, int winner, int pen) {
        PredictionScore s = PredictionScore.builder()
                .prediction(prediction)
                .points(points)
                .exactScorePoints(exact)
                .winnerPoints(winner)
                .penaltiesPoints(pen)
                .calculatedAt(Instant.parse("2026-06-12T20:00:00Z"))
                .build();
        em.persist(s);
        em.flush();
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
