package org.danielesteban.worldcupbetbackend.integration;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.StageRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.TeamRepository;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalTeamDto;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class DataSeederPropertyTest {

    // Feature: football-data-integration, Property 10: Siembra de datos es idempotente
    // Validates: Requirements 8.1, 8.2, 8.3, 10.1, 10.2, 10.3, 11.3
    @Property(tries = 100)
    void seedAllIsIdempotent(@ForAll("seedRunCounts") int runCount) {
        // Setup mocks
        FootballDataClient client = mock(FootballDataClient.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        StageRepository stageRepository = mock(StageRepository.class);
        StatusMapper statusMapper = new StatusMapper();

        // Fixed external data
        List<ExternalTeamDto> teams = List.of(
                new ExternalTeamDto(1, "Argentina", "ARG", "https://crests/1.svg"),
                new ExternalTeamDto(2, "Brazil", "BRA", "https://crests/2.svg")
        );

        List<ExternalMatchDto> matches = List.of(
                new ExternalMatchDto(100, "SCHEDULED", null, null, null, null,
                        1, 2, "GROUP_STAGE", Instant.parse("2026-06-15T18:00:00Z"))
        );

        when(client.fetchTeams()).thenReturn(teams);
        when(client.fetchMatches()).thenReturn(matches);

        // Track created entities to simulate database state
        Map<Integer, Team> teamStore = new HashMap<>();
        Map<Integer, Match> matchStore = new HashMap<>();

        Stage groupStage = new Stage();
        groupStage.setName("GROUP_STAGE");
        when(stageRepository.findByName("GROUP_STAGE")).thenReturn(Optional.of(groupStage));

        // Simulate team repository behavior
        when(teamRepository.findByExternalId(anyInt())).thenAnswer(invocation -> {
            Integer extId = invocation.getArgument(0);
            return Optional.ofNullable(teamStore.get(extId));
        });

        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            if (team.getExternalId() != null) {
                teamStore.put(team.getExternalId(), team);
            }
            return team;
        });

        // Simulate match repository behavior
        when(matchRepository.findByExternalId(anyInt())).thenAnswer(invocation -> {
            Integer extId = invocation.getArgument(0);
            return Optional.ofNullable(matchStore.get(extId));
        });

        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            if (match.getExternalId() != null) {
                matchStore.put(match.getExternalId(), match);
            }
            return match;
        });

        DataSeeder seeder = new DataSeeder(client, teamRepository, matchRepository, stageRepository, statusMapper);

        // Run seedAll() N times
        DataSeeder.SeedResult firstResult = null;
        DataSeeder.SeedResult lastResult = null;

        for (int i = 0; i < runCount; i++) {
            lastResult = seeder.seedAll();
            if (i == 0) {
                firstResult = lastResult;
            }
        }

        // After all runs, the store should have the same number of entities
        assertThat(teamStore).hasSize(2);
        assertThat(matchStore).hasSize(1);

        // First run creates, subsequent runs update (no new creates)
        if (runCount > 1) {
            // Last run should have 0 created (all updates)
            assertThat(lastResult.created()).isEqualTo(0);
            assertThat(lastResult.updated()).isEqualTo(3); // 2 teams + 1 match
        }

        // Verify final state matches the input data
        Team argentina = teamStore.get(1);
        assertThat(argentina.getName()).isEqualTo("Argentina");
        assertThat(argentina.getCode()).isEqualTo("ARG");

        Team brazil = teamStore.get(2);
        assertThat(brazil.getName()).isEqualTo("Brazil");
        assertThat(brazil.getCode()).isEqualTo("BRA");

        Match match = matchStore.get(100);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
    }

    @Provide
    Arbitrary<Integer> seedRunCounts() {
        return Arbitraries.integers().between(1, 5);
    }
}
