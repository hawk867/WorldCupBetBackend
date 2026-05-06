package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.service.event.MatchAdjustedEvent;
import org.danielesteban.worldcupbetbackend.service.event.MatchFinishedEvent;
import org.danielesteban.worldcupbetbackend.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MatchServiceTest {

    private MatchRepository matchRepository;
    private ApplicationEventPublisher eventPublisher;
    private SimpMessagingTemplate messagingTemplate;
    private MatchService matchService;

    @BeforeEach
    void setUp() {
        matchRepository = mock(MatchRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        matchService = new MatchService(matchRepository, eventPublisher, messagingTemplate);
    }

    @Test
    void transitionToFinished_publishesMatchFinishedEvent() {
        Match match = Match.builder().id(1L).status(MatchStatus.LIVE).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        matchService.transitionStatus(1L, MatchStatus.FINISHED);

        verify(eventPublisher).publishEvent(new MatchFinishedEvent(1L));
    }

    @Test
    void transitionToAdjusted_publishesMatchAdjustedEvent() {
        Match match = Match.builder().id(1L).status(MatchStatus.FINISHED).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        matchService.transitionStatus(1L, MatchStatus.ADJUSTED);

        verify(eventPublisher).publishEvent(new MatchAdjustedEvent(1L));
    }

    @Test
    void transitionStatus_publishesWebSocket() {
        Match match = Match.builder().id(1L).status(MatchStatus.SCHEDULED).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        matchService.transitionStatus(1L, MatchStatus.LIVE);

        verify(messagingTemplate).convertAndSend(eq("/topic/matches/1"), any(Match.class));
    }

    @Test
    void updateScore_publishesWebSocket() {
        Match match = Match.builder().id(1L).status(MatchStatus.LIVE).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        matchService.updateScore(1L, 2, 1);

        verify(messagingTemplate).convertAndSend(eq("/topic/matches/1"), any(Match.class));
    }

    @Test
    void findById_nonExistentMatch_throwsResourceNotFoundException() {
        when(matchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
