package org.danielesteban.worldcupbetbackend.domain.entity;

import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.danielesteban.worldcupbetbackend.support.ConstraintAssertions;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests for {@link Match}.
 * <p>
 * Covers the database-enforced invariants specified by
 * {@code V1__baseline_schema.sql}:
 * <ul>
 *   <li>Unique {@code external_id} ({@code uk_matches_external_id}).</li>
 *   <li>Distinct home and away teams ({@code chk_matches_distinct_teams}).</li>
 *   <li>Non-negative goals and penalties; {@code NULL} is allowed before a
 *       result exists.</li>
 *   <li>Penalty consistency when {@link Match#isWentToPenalties()} is true
 *       ({@code chk_matches_penalties_consistency}).</li>
 *   <li>Status domain enforced by {@code chk_matches_status}.</li>
 *   <li>Lazy loading of associations ({@code stage}, {@code homeTeam},
 *       {@code awayTeam}).</li>
 * </ul>
 */
class MatchPersistenceIT extends AbstractRepositoryIT {

    /**
     * Increments across test methods (different test classes share this JVM,
     * but each test runs in its own rolled-back transaction, so the shared state
     * is fine). Used to produce unique {@code external_id} values without
     * depending on insertion order.
     */
    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(10_000);

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("persisting a valid match assigns an id and populates updatedAt")
    void persistingValidMatchAssignsIdAndUpdatedAt() {
        Match match = em.persistFlushFind(buildSchedulableMatch());

        assertThat(match.getId()).isNotNull();
        assertThat(match.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("many-to-one associations are lazily loaded until accessed")
    void manyToOneAssociationsAreLazy() {
        Match persisted = em.persistFlushFind(buildSchedulableMatch());

        // Clear the persistence context so further reads hit the DB.
        em.clear();

        Match reloaded = em.find(Match.class, persisted.getId());

        assert reloaded != null;
        assertThat(Hibernate.isInitialized(reloaded.getStage())).isFalse();
        assertThat(Hibernate.isInitialized(reloaded.getHomeTeam())).isFalse();
        assertThat(Hibernate.isInitialized(reloaded.getAwayTeam())).isFalse();

        // Triggering real access initializes the proxy.
        assertThat(Hibernate.isInitialized(reloaded.getStage().getName())).isTrue();
    }

    @Test
    @DisplayName("two matches with the same external_id are rejected by uk_matches_external_id")
    void duplicateExternalIdIsRejected() {
        Integer externalId = EXTERNAL_ID_SEQ.incrementAndGet();
        em.persistAndFlush(buildSchedulableMatch(externalId));

        Match duplicate = buildSchedulableMatch(externalId);

        ConstraintAssertions.assertViolates("uk_matches_external_id",
                () -> em.persistAndFlush(duplicate));
    }

    @Test
    @DisplayName("a match with the same home and away team is rejected by chk_matches_distinct_teams")
    void sameHomeAndAwayTeamIsRejected() {
        Team team = em.persistFlushFind(team(EXTERNAL_ID_SEQ.incrementAndGet(), "Solo"));

        Match invalid = Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(groupStage())
                .homeTeam(team)
                .awayTeam(team)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.SCHEDULED)
                .build();

        ConstraintAssertions.assertViolates("chk_matches_distinct_teams",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("a negative homeGoals value is rejected by chk_matches_home_goals")
    void negativeHomeGoalsIsRejected() {
        Match invalid = buildSchedulableMatch();
        invalid.setHomeGoals(-1);

        ConstraintAssertions.assertViolates("chk_matches_home_goals",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("a negative awayGoals value is rejected by chk_matches_away_goals")
    void negativeAwayGoalsIsRejected() {
        Match invalid = buildSchedulableMatch();
        invalid.setAwayGoals(-1);

        ConstraintAssertions.assertViolates("chk_matches_away_goals",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("a negative homePenalties value is rejected by chk_matches_home_penalties")
    void negativeHomePenaltiesIsRejected() {
        Match invalid = buildSchedulableMatch();
        invalid.setWentToPenalties(true);
        invalid.setHomePenalties(-1);
        invalid.setAwayPenalties(3);

        ConstraintAssertions.assertViolates("chk_matches_home_penalties",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("a negative awayPenalties value is rejected by chk_matches_away_penalties")
    void negativeAwayPenaltiesIsRejected() {
        Match invalid = buildSchedulableMatch();
        invalid.setWentToPenalties(true);
        invalid.setHomePenalties(4);
        invalid.setAwayPenalties(-1);

        ConstraintAssertions.assertViolates("chk_matches_away_penalties",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("null goals and penalties are accepted before the match has a result")
    void nullScoresAreAcceptedBeforeResult() {
        Match match = buildSchedulableMatch();
        // All score columns are left null by default.

        Match saved = em.persistFlushFind(match);

        assertThat(saved.getHomeGoals()).isNull();
        assertThat(saved.getAwayGoals()).isNull();
        assertThat(saved.getHomePenalties()).isNull();
        assertThat(saved.getAwayPenalties()).isNull();
    }

    @Test
    @DisplayName("wentToPenalties=true with null penalty columns is rejected")
    void wentToPenaltiesWithoutPenaltyValuesIsRejected() {
        Match invalid = buildSchedulableMatch();
        invalid.setWentToPenalties(true);
        // homePenalties / awayPenalties remain null.

        ConstraintAssertions.assertViolates("chk_matches_penalties_consistency",
                () -> em.persistAndFlush(invalid));
    }

    @Test
    @DisplayName("an invalid status value is rejected by chk_matches_status")
    void invalidStatusIsRejectedByCheckConstraint() {
        // Insert a new row via native SQL with the foreign keys of an existing
        // match, so the only offending column is status.
        Stage stage = groupStage();
        Team home = em.persistFlushFind(team(EXTERNAL_ID_SEQ.incrementAndGet(), "Home"));
        Team away = em.persistFlushFind(team(EXTERNAL_ID_SEQ.incrementAndGet(), "Away"));
        Integer externalId = EXTERNAL_ID_SEQ.incrementAndGet();

        jakarta.persistence.Query insert = em.getEntityManager().createNativeQuery("""
                INSERT INTO matches
                    (external_id, stage_id, home_team_id, away_team_id,
                     kickoff_at, status, went_to_penalties)
                VALUES
                    (?1, ?2, ?3, ?4, ?5, 'BOGUS', false)
                """)
                .setParameter(1, externalId)
                .setParameter(2, stage.getId())
                .setParameter(3, home.getId())
                .setParameter(4, away.getId())
                .setParameter(5, Instant.parse("2026-06-11T20:00:00Z"));

        ConstraintAssertions.assertViolates("chk_matches_status", insert::executeUpdate);
    }

    // --- fixtures ------------------------------------------------------------

    private Match buildSchedulableMatch() {
        return buildSchedulableMatch(EXTERNAL_ID_SEQ.incrementAndGet());
    }

    private Match buildSchedulableMatch(Integer externalId) {
        Team home = em.persistFlushFind(team(EXTERNAL_ID_SEQ.incrementAndGet(), "Home"));
        Team away = em.persistFlushFind(team(EXTERNAL_ID_SEQ.incrementAndGet(), "Away"));
        return Match.builder()
                .externalId(externalId)
                .stage(groupStage())
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-11T20:00:00Z"))
                .status(MatchStatus.SCHEDULED)
                .build();
    }

    private Team team(Integer externalId, String name) {
        return Team.builder()
                .externalId(externalId)
                .name(name + "-" + externalId)
                .build();
    }

    private Stage groupStage() {
        return em.getEntityManager()
                .createQuery("SELECT s FROM Stage s WHERE s.name = 'GROUP_STAGE'", Stage.class)
                .getSingleResult();
    }
}
