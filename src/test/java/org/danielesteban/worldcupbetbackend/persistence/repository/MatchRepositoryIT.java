package org.danielesteban.worldcupbetbackend.persistence.repository;

import jakarta.persistence.EntityManager;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.support.AbstractRepositoryIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration tests for {@link MatchRepository}.
 */
@SuppressWarnings("resource") // Shared, Spring-managed EntityManager; see UserScorePersistenceIT for rationale.
class MatchRepositoryIT extends AbstractRepositoryIT {

    private static final AtomicInteger EXTERNAL_ID_SEQ = new AtomicInteger(100_000);

    @Autowired
    private MatchRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    @Test
    @DisplayName("findByExternalId returns the match when present")
    void findByExternalIdReturnsMatchWhenPresent() {
        Match scheduled = persistMatch(MatchStatus.SCHEDULED, Instant.parse("2026-06-11T20:00:00Z"));

        assertThat(repository.findByExternalId(scheduled.getExternalId()))
                .get()
                .extracting(Match::getId)
                .isEqualTo(scheduled.getId());
    }

    @Test
    @DisplayName("findAllByStatus returns only matches in the given status")
    void findAllByStatusFilters() {
        Match live = persistMatch(MatchStatus.LIVE, Instant.parse("2026-06-12T20:00:00Z"));
        persistMatch(MatchStatus.SCHEDULED, Instant.parse("2026-06-13T20:00:00Z"));

        List<Match> liveMatches = repository.findAllByStatus(MatchStatus.LIVE);

        assertThat(liveMatches).extracting(Match::getId).contains(live.getId());
        assertThat(liveMatches).allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(MatchStatus.LIVE));
    }

    @Test
    @DisplayName("findAllByStatusIn returns matches across multiple statuses")
    void findAllByStatusInReturnsUnion() {
        Match live = persistMatch(MatchStatus.LIVE, Instant.parse("2026-06-14T20:00:00Z"));
        Match finished = persistMatch(MatchStatus.FINISHED, Instant.parse("2026-06-15T20:00:00Z"));

        List<Match> matches = repository.findAllByStatusIn(EnumSet.of(MatchStatus.LIVE, MatchStatus.FINISHED));

        assertThat(matches).extracting(Match::getId).contains(live.getId(), finished.getId());
    }

    @Test
    @DisplayName("findAllByKickoffAtBeforeAndStatus returns matches past kickoff still SCHEDULED")
    void findAllByKickoffAtBeforeAndStatusReturnsOverdue() {
        Instant past = Instant.parse("2026-05-01T20:00:00Z");
        Instant future = Instant.parse("2099-01-01T20:00:00Z");
        Match overdue = persistMatch(MatchStatus.SCHEDULED, past);
        persistMatch(MatchStatus.SCHEDULED, future);

        List<Match> matches = repository.findAllByKickoffAtBeforeAndStatus(
                Instant.parse("2026-06-01T00:00:00Z"), MatchStatus.SCHEDULED);

        assertThat(matches).extracting(Match::getId).contains(overdue.getId());
        assertThat(matches).allSatisfy(m -> assertThat(m.getKickoffAt()).isBefore(Instant.parse("2026-06-01T00:00:00Z")));
    }

    @Test
    @DisplayName("findAllByStageIdOrderByKickoffAtAsc returns stage matches sorted by kickoff")
    void findAllByStageIdOrderByKickoffAtAscSorts() {
        Stage stage = groupStage();
        Match later  = persistMatchOnStage(stage, Instant.parse("2026-06-20T20:00:00Z"));
        Match sooner = persistMatchOnStage(stage, Instant.parse("2026-06-19T20:00:00Z"));

        List<Match> ordered = repository.findAllByStageIdOrderByKickoffAtAsc(stage.getId());

        List<Long> ids = ordered.stream().map(Match::getId).toList();
        assertThat(ids).containsSubsequence(sooner.getId(), later.getId());
    }

    // --- fixtures ------------------------------------------------------------

    private Match persistMatch(MatchStatus status, Instant kickoffAt) {
        return persistMatchOnStage(groupStage(), status, kickoffAt);
    }

    private Match persistMatchOnStage(Stage stage, Instant kickoffAt) {
        return persistMatchOnStage(stage, MatchStatus.SCHEDULED, kickoffAt);
    }

    private Match persistMatchOnStage(Stage stage, MatchStatus status, Instant kickoffAt) {
        Team home = team("Home");
        Team away = team("Away");
        em().persist(home);
        em().persist(away);
        em().flush();

        Match match = Match.builder()
                .externalId(EXTERNAL_ID_SEQ.incrementAndGet())
                .stage(stage)
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(kickoffAt)
                .status(status)
                .build();
        em().persist(match);
        em().flush();
        return match;
    }

    private Team team(String nameBase) {
        int externalId = EXTERNAL_ID_SEQ.incrementAndGet();
        return Team.builder().externalId(externalId).name(nameBase + "-" + externalId).build();
    }

    private Stage groupStage() {
        return em()
                .createQuery("SELECT s FROM Stage s WHERE s.name = 'GROUP_STAGE'", Stage.class)
                .getSingleResult();
    }
}
