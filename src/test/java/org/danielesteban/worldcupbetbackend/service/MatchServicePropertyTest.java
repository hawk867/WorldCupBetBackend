package org.danielesteban.worldcupbetbackend.service;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.service.exception.IllegalStateTransitionException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Feature: service-layer
 * Property 5: Validez de transiciones de la máquina de estados
 */
@SuppressWarnings("unused")
class MatchServicePropertyTest {

    private MatchService buildService(Match match) {
        MatchRepository repo = mock(MatchRepository.class);
        when(repo.findById(match.getId())).thenReturn(Optional.of(match));
        return new MatchService(repo, event -> {}, mock(org.springframework.messaging.simp.SimpMessagingTemplate.class));
    }

    // Property 5: Validez de transiciones de la máquina de estados
    @Property(tries = 200)
    void onlyValidTransitionsSucceed(
            @ForAll("matchStatuses") MatchStatus from,
            @ForAll("matchStatuses") MatchStatus to) {

        Match match = Match.builder().id(1L).status(from).build();
        MatchService service = buildService(match);

        if (MatchStateMachine.isValidTransition(from, to)) {
            assertThatCode(() -> service.transitionStatus(1L, to))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> service.transitionStatus(1L, to))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Provide
    Arbitrary<MatchStatus> matchStatuses() {
        return Arbitraries.of(MatchStatus.values());
    }
}
