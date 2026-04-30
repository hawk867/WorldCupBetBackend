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
 * Persistence integration tests for {@link Prediction}.
 * <p>
 * Covers the database-enforced invariants declared in
 * {@code V1__baseline_schema.sql}:
 * <ul>
 *   <li>Composite unique constraint on {@code (user_id, match_id)}
 *       ({@code uk_predictions_user_match}).</li>
 *   <li>Non-null {@code home_goals} / {@code away_goals}.</li>
 *   <li>Non-negative {@code home_goals} / {@code away_goals}
 *       ({@code chk_predictions_*_goals}).</li>
 *   <li>Penalty pairing: both null or both non-null, enforced by
 *       {@code chk_predictions_penalties_pair}.</li>
 *   <li>JPA auditing: {@code createdAt} populated on insert, {@code updatedAt}
 *       advances on update while {@code createdAt} remains stable.</li>
 * </ul>
 */
class PredictionPersistenceIT extends AbstractRepositoryIT {

    /** Fresh external ids so this test's fixtures never collide with others. */
    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(40_000);
    private static final AtomicLong EMAIL_SEQ = new AtomicLong();

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("persisting a valid prediction populates id and audit timestamps")
    void persistingValidPredictionPopulatesIdAndAuditTimestamps() {
        Prediction saved = em.persistFlushFind(buildValidPrediction());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("two predictions with the same (user, match) are rejected by uk_predictions_user_match")
    void duplicateUserMatchIsRejected() {
        User user = persistUser();
        Match match = persistMatch();

        em.persistAndFlush(predictionFor(user, match, 1, 0));

        Prediction duplicate = predictionFor(user, match, 2, 2);

        ConstraintAssertions.assertViolates("uk_predictions_user_match",
                () -> em.persistAndFlush(duplicate));
    }

    @Test
    @DisplayName("a negative homeGoals value is rejected by chk_predictions_home_goals")
    void negativeHomeGoalsIsRejected() {
        Prediction invalid = buildValidPrediction();
        invalid.setHomeGoals(-1);

        ConstraintAssertions.assertViolates("chk_predictions_home_goals",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("a negative awayGoals value is rejected by chk_predictions_away_goals")
    void negativeAwayGoalsIsRejected() {
        Prediction invalid = buildValidPrediction();
        invalid.setAwayGoals(-1);

        ConstraintAssertions.assertViolates("chk_predictions_away_goals",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("a prediction with only one of (home, away) penalties is rejected")
    void unpairedPenaltiesAreRejected() {
        Prediction invalid = buildValidPrediction();
        invalid.setHomePenalties(4);
        invalid.setAwayPenalties(null);

        ConstraintAssertions.assertViolates("chk_predictions_penalties_pair",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("both penalty fields null and both non-null are accepted")
    void pairedPenaltiesAreAccepted() {
        Prediction bothNull = buildValidPrediction();
        Prediction bothSet = buildValidPrediction();
        bothSet.setHomePenalties(5);
        bothSet.setAwayPenalties(3);

        assertThat(em.persistFlushFind(bothNull).getId()).isNotNull();
        assertThat(em.persistFlushFind(bothSet).getId()).isNotNull();
    }

    @Test
    @DisplayName("updating a prediction advances updatedAt while createdAt stays the same")
    void updateAdvancesUpdatedAtButKeepsCreatedAt() throws InterruptedException {
        Prediction saved = em.persistFlushFind(buildValidPrediction());
        Instant originalCreatedAt = saved.getCreatedAt();
        Instant originalUpdatedAt = saved.getUpdatedAt();

        // Ensure enough wall-clock drift so the timestamp comparison is meaningful
        // even on fast systems with millisecond-resolution timers.
        Thread.sleep(20);

        saved.setHomeGoals(saved.getHomeGoals() + 1);
        em.persistAndFlush(saved);
        em.clear();

        Prediction reloaded = em.find(Prediction.class, saved.getId());

        assert reloaded != null;
        assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        assertThat(reloaded.getUpdatedAt()).isAfter(originalCreatedAt);
    }

    // --- fixtures ------------------------------------------------------------

    private Prediction buildValidPrediction() {
        return predictionFor(persistUser(), persistMatch(), 2, 1);
    }

    private Prediction predictionFor(User user, Match match, int homeGoals, int awayGoals) {
        return Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(homeGoals)
                .awayGoals(awayGoals)
                .build();
    }

    private User persistUser() {
        return em.persistFlushFind(User.builder()
                .email("predictor-" + EMAIL_SEQ.incrementAndGet() + "@example.com")
                .passwordHash("hash")
                .fullName("Predictor")
                .role(UserRole.USER)
                .passwordChanged(false)
                .build());
    }

    private Match persistMatch() {
        Team home = em.persistFlushFind(team("Home"));
        Team away = em.persistFlushFind(team("Away"));
        return em.persistFlushFind(Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.SCHEDULED)
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
