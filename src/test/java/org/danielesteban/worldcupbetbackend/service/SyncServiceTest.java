package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SyncService}.
 * Validates: Requirements 16.1, 16.3, 16.4, 16.5, 16.6
 */
class SyncServiceTest {

    private MatchRepository matchRepository;
    private MatchService matchService;
    private FootballDataClient footballDataClient;
    private SyncService syncService;

    @BeforeEach
    void setUp() {
        matchRepository = mock(MatchRepository.class);
        matchService = mock(MatchService.class);
        footballDataClient = mock(FootballDataClient.class);
        syncService = new SyncService(matchRepository, matchService, footballDataClient);
    }

    @Test
    void syncMatches_noLiveOrOverdueMatches_doesNothing() {
        when(matchRepository.findAllByStatus(MatchStatus.LIVE)).thenReturn(Collections.emptyList());
        when(matchRepository.findAllByKickoffAtBeforeAndStatus(any(Instant.class), eq(MatchStatus.SCHEDULED)))
                .thenReturn(Collections.emptyList());

        syncService.syncMatches();

        verifyNoInteractions(footballDataClient);
        verifyNoInteractions(matchService);
    }

    @Test
    void syncMatches_apiDown_logsWarningAndDoesNotThrow() {
        Match liveMatch = Match.builder().id(1L).externalId(100).status(MatchStatus.LIVE).build();
        when(matchRepository.findAllByStatus(MatchStatus.LIVE)).thenReturn(List.of(liveMatch));
        when(matchRepository.findAllByKickoffAtBeforeAndStatus(any(Instant.class), eq(MatchStatus.SCHEDULED)))
                .thenReturn(Collections.emptyList());
        when(footballDataClient.fetchMatches())
                .thenThrow(new ExternalApiException("Connection refused", new RuntimeException()));

        assertThatCode(() -> syncService.syncMatches()).doesNotThrowAnyException();
    }

    @Test
    void transitionOverdueMatches_transitionsScheduledMatchesPastKickoff() {
        Match overdueMatch1 = Match.builder()
                .id(1L)
                .status(MatchStatus.SCHEDULED)
                .kickoffAt(Instant.now().minusSeconds(3600))
                .build();
        Match overdueMatch2 = Match.builder()
                .id(2L)
                .status(MatchStatus.SCHEDULED)
                .kickoffAt(Instant.now().minusSeconds(1800))
                .build();

        when(matchRepository.findAllByKickoffAtBeforeAndStatus(any(Instant.class), eq(MatchStatus.SCHEDULED)))
                .thenReturn(List.of(overdueMatch1, overdueMatch2));

        syncService.transitionOverdueMatches();

        verify(matchService).transitionStatus(1L, MatchStatus.LIVE);
        verify(matchService).transitionStatus(2L, MatchStatus.LIVE);
    }

    @Test
    void syncLiveMatches_updatesScoreWhenChanged() {
        Match liveMatch = Match.builder()
                .id(1L)
                .externalId(100)
                .status(MatchStatus.LIVE)
                .homeGoals(0)
                .awayGoals(0)
                .build();

        ExternalMatchDto externalDto = new ExternalMatchDto(100, "IN_PLAY", 2, 1, null, null);

        when(matchRepository.findAllByStatus(MatchStatus.LIVE)).thenReturn(List.of(liveMatch));
        when(footballDataClient.fetchMatches()).thenReturn(List.of(externalDto));

        syncService.syncLiveMatches();

        verify(matchService).updateScore(1L, 2, 1);
        verify(matchService, never()).transitionStatus(anyLong(), any());
    }

    @Test
    void syncLiveMatches_transitionsToFinishedWhenApiReportsFinished() {
        Match liveMatch = Match.builder()
                .id(1L)
                .externalId(100)
                .status(MatchStatus.LIVE)
                .homeGoals(2)
                .awayGoals(1)
                .build();

        ExternalMatchDto externalDto = new ExternalMatchDto(100, "FINISHED", 2, 1, null, null);

        when(matchRepository.findAllByStatus(MatchStatus.LIVE)).thenReturn(List.of(liveMatch));
        when(footballDataClient.fetchMatches()).thenReturn(List.of(externalDto));

        syncService.syncLiveMatches();

        verify(matchService).transitionStatus(1L, MatchStatus.FINISHED);
        // Score hasn't changed, so updateScore should NOT be called
        verify(matchService, never()).updateScore(anyLong(), anyInt(), anyInt());
    }

    @Test
    void syncLiveMatches_noChangeDoesNotUpdate() {
        Match liveMatch = Match.builder()
                .id(1L)
                .externalId(100)
                .status(MatchStatus.LIVE)
                .homeGoals(1)
                .awayGoals(0)
                .build();

        ExternalMatchDto externalDto = new ExternalMatchDto(100, "IN_PLAY", 1, 0, null, null);

        when(matchRepository.findAllByStatus(MatchStatus.LIVE)).thenReturn(List.of(liveMatch));
        when(footballDataClient.fetchMatches()).thenReturn(List.of(externalDto));

        syncService.syncLiveMatches();

        verify(matchService, never()).updateScore(anyLong(), anyInt(), anyInt());
        verify(matchService, never()).transitionStatus(anyLong(), any());
    }
}
