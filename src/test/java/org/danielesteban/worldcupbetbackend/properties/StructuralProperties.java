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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the structural (cross-column) invariants declared
 * in {@code design.md § Correctness Properties}:
 * <ul>
 *   <li>Property 5 – Match distinct teams ({@code home_team_id <> away_team_id}).</li>
 *   <li>Property 8 – Match penalty consistency (when
 *       {@code wentToPenalties = TRUE}, both penalty columns must be
 *       non-null).</li>
 *   <li>Property 11 – Prediction penalty pairing (both penalty columns
 *       null or both non-null).</li>
 *   <li>Property 13 – PredictionScore decomposition identity
 *       ({@code points = exactScorePoints + winnerPoints + penaltiesPoints}).</li>
 * </ul>
 * <p>
 * Each property runs {@value #TRIES} iterations with randomly generated
 * inputs. Every iteration executes in its own {@code REQUIRES_NEW}
 * transaction rolled back at the end; see {@code UniquenessProperties} for
 * the rationale behind driving the property loop ourselves instead of using
 * jqwik's JUnit engine.
 */
class StructuralPropertiesTest extends AbstractRepositoryIT {

    private static final int TRIES = 100;
    private static final long RNG_SEED = 1337;

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(400_000);
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

    // --- Property 5: Match distinct teams -----------------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 5: Match distinct teams")
    @DisplayName("Property 5: any Match with home_team_id == away_team_id is rejected")
    void property5_matchDistinctTeams() {
        runProperty(rnd -> EXTERNAL_ID_SEQ.incrementAndGet(), externalId -> {
            // Persist a single team, then attempt to use it on both sides.
            Team team = persistTeam();

            Match invalid = Match.builder()
                    .externalId(externalId)
                    .stage(groupStage())
                    .homeTeam(team)
                    .awayTeam(team)
                    .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                    .status(MatchStatus.SCHEDULED)
                    .build();

            ConstraintAssertions.assertViolates("chk_matches_distinct_teams",
                    () -> { em.persist(invalid); em.flush(); });
        });
    }

    // --- Property 8: Match penalty consistency ------------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 8: Match penalty consistency")
    @DisplayName("Property 8: wentToPenalties=true with a missing penalty column is rejected")
    void property8_matchPenaltyConsistency() {
        // Three failing configurations per iteration:
        //   0 -> home set, away null
        //   1 -> home null, away set
        //   2 -> both null
        runProperty(rnd -> new int[] { rnd.nextInt(3), rnd.nextInt(10) + 1 }, sample -> {
            int mode = sample[0];
            int value = sample[1];
            Match match = validSchedulableMatch();
            match.setWentToPenalties(true);
            switch (mode) {
                case 0 -> { match.setHomePenalties(value); match.setAwayPenalties(null); }
                case 1 -> { match.setHomePenalties(null); match.setAwayPenalties(value); }
                case 2 -> { match.setHomePenalties(null); match.setAwayPenalties(null); }
                default -> throw new IllegalStateException();
            }

            ConstraintAssertions.assertViolates("chk_matches_penalties_consistency",
                    () -> { em.persist(match); em.flush(); });
        });
    }

    // --- Property 11: Prediction penalty pairing ----------------------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 11: Prediction penalty pairing")
    @DisplayName("Property 11: Prediction with exactly one penalty column null is rejected")
    void property11_predictionPenaltyPairing() {
        // Two failing configurations per iteration:
        //   0 -> home set, away null
        //   1 -> home null, away set
        //
        // Each iteration also exercises both valid pairings (both null and
        // both set) to guarantee those are accepted, which reinforces the
        // biconditional expressed by the design property.
        runProperty(rnd -> new int[] { rnd.nextInt(2), rnd.nextInt(10) + 1 }, sample -> {
            int mode = sample[0];
            int value = sample[1];
            User user = persistUser();
            Match match = persistMatch();

            Prediction invalid = Prediction.builder()
                    .user(user)
                    .match(match)
                    .homeGoals(1)
                    .awayGoals(1)
                    .homePenalties(mode == 0 ? value : null)
                    .awayPenalties(mode == 0 ? null : value)
                    .build();

            ConstraintAssertions.assertViolates("chk_predictions_penalties_pair",
                    () -> { em.persist(invalid); em.flush(); });
        });
    }

    @Test
    @Tag("Feature: domain-model-persistence, Property 11b: Prediction valid pairings are accepted")
    @DisplayName("Property 11b: both-null and both-non-null penalty pairings are accepted")
    void property11b_predictionValidPenaltyPairings() {
        // Complementary positive property: generate random valid pairings
        // (both null, both set) and verify they are accepted. Without this
        // positive case we can only claim "invalid is rejected"; adding it
        // makes the biconditional of Requirement 3.10 fully evident.
        runProperty(rnd -> new int[] { rnd.nextInt(2), rnd.nextInt(10), rnd.nextInt(10) }, sample -> {
            int mode = sample[0];
            int h = sample[1];
            int a = sample[2];
            User user = persistUser();
            Match match = persistMatch();

            Prediction valid = Prediction.builder()
                    .user(user)
                    .match(match)
                    .homeGoals(1)
                    .awayGoals(1)
                    .homePenalties(mode == 0 ? null : h)
                    .awayPenalties(mode == 0 ? null : a)
                    .build();

            em.persist(valid);
            em.flush();
            assertThat(valid.getId()).isNotNull();
        });
    }

    // --- Property 13: PredictionScore decomposition identity ----------------

    @Test
    @Tag("Feature: domain-model-persistence, Property 13: PredictionScore points decomposition")
    @DisplayName("Property 13: PredictionScore with points != sum(components) is rejected")
    void property13_predictionScoreDecomposition() {
        runProperty(rnd -> new int[] {
                rnd.nextInt(5),           // exact
                rnd.nextInt(5),           // winner
                rnd.nextInt(5),           // pen
                rnd.nextInt(10) + 1       // non-zero offset to break the sum
        }, sample -> {
            int exact  = sample[0];
            int winner = sample[1];
            int pen    = sample[2];
            int offset = sample[3];
            int mismatchedPoints = exact + winner + pen + offset;

            Prediction prediction = persistPrediction(persistUser(), persistMatch());

            PredictionScore invalid = PredictionScore.builder()
                    .prediction(prediction)
                    .points(mismatchedPoints)
                    .exactScorePoints(exact)
                    .winnerPoints(winner)
                    .penaltiesPoints(pen)
                    .calculatedAt(Instant.parse("2026-06-12T20:00:00Z"))
                    .build();

            ConstraintAssertions.assertViolates("chk_prediction_scores_sum",
                    () -> { em.persist(invalid); em.flush(); });
        });
    }

    @Test
    @Tag("Feature: domain-model-persistence, Property 13b: Balanced decompositions are accepted")
    @DisplayName("Property 13b: PredictionScore with points == sum(components) is accepted")
    void property13b_predictionScoreBalancedIsAccepted() {
        runProperty(rnd -> new int[] {
                rnd.nextInt(5), rnd.nextInt(5), rnd.nextInt(5)
        }, sample -> {
            int exact  = sample[0];
            int winner = sample[1];
            int pen    = sample[2];
            int points = exact + winner + pen;

            Prediction prediction = persistPrediction(persistUser(), persistMatch());

            PredictionScore valid = PredictionScore.builder()
                    .prediction(prediction)
                    .points(points)
                    .exactScorePoints(exact)
                    .winnerPoints(winner)
                    .penaltiesPoints(pen)
                    .calculatedAt(Instant.parse("2026-06-12T20:00:00Z"))
                    .build();

            em.persist(valid);
            em.flush();
            assertThat(valid.getId()).isNotNull();
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
                .email("struct-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("h")
                .fullName("Structural User")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Team persistTeam() {
        Team t = team("T");
        em.persist(t);
        em.flush();
        return t;
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
