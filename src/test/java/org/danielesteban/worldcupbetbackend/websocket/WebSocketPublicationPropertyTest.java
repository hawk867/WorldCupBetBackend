package org.danielesteban.worldcupbetbackend.websocket;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.service.MatchService;
import org.danielesteban.worldcupbetbackend.websocket.dto.MatchUpdateMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebSocketPublicationPropertyTest {

    // Feature: websocket-realtime, Property 1: Publicación de actualización de partido en topic correcto
    @Property(tries = 100)
    void updateScorePublishesToCorrectTopic(
            @ForAll("matchIds") Long matchId,
            @ForAll("goals") Integer homeGoals,
            @ForAll("goals") Integer awayGoals) {
        // **Validates: Requirements 3.1, 3.2**
        MatchRepository matchRepository = mock(MatchRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

        Match match = Match.builder()
            .id(matchId)
            .status(MatchStatus.LIVE)
            .homeGoals(0)
            .awayGoals(0)
            .wentToPenalties(false)
            .updatedAt(Instant.now())
            .build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchService matchService = new MatchService(matchRepository, eventPublisher, messagingTemplate);
        matchService.updateScore(matchId, homeGoals, awayGoals);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
            eq("/topic/matches/" + matchId),
            payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isInstanceOf(MatchUpdateMessage.class);
        MatchUpdateMessage message = (MatchUpdateMessage) payloadCaptor.getValue();
        assertThat(message.matchId()).isEqualTo(matchId);
        assertThat(message.homeGoals()).isEqualTo(homeGoals);
        assertThat(message.awayGoals()).isEqualTo(awayGoals);
    }

    // Feature: websocket-realtime, Property 6: Resiliencia ante errores de publicación
    @Property(tries = 100)
    void updateScoreDoesNotPropagateWebSocketErrors(
            @ForAll("matchIds") Long matchId,
            @ForAll("goals") Integer homeGoals,
            @ForAll("goals") Integer awayGoals) {
        // **Validates: Requirements 10.1, 10.2**
        MatchRepository matchRepository = mock(MatchRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

        Match match = Match.builder()
            .id(matchId)
            .status(MatchStatus.LIVE)
            .homeGoals(0)
            .awayGoals(0)
            .wentToPenalties(false)
            .updatedAt(Instant.now())
            .build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        doThrow(new RuntimeException("WebSocket broker unavailable"))
            .when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        MatchService matchService = new MatchService(matchRepository, eventPublisher, messagingTemplate);

        assertThatCode(() -> matchService.updateScore(matchId, homeGoals, awayGoals))
            .doesNotThrowAnyException();
    }

    @Provide
    Arbitrary<Long> matchIds() {
        return Arbitraries.longs().between(1, 10_000);
    }

    @Provide
    Arbitrary<Integer> goals() {
        return Arbitraries.integers().between(0, 15);
    }
}
