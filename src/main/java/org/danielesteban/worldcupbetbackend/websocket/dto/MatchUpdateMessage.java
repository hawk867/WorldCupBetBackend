package org.danielesteban.worldcupbetbackend.websocket.dto;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;

import java.time.Instant;

/**
 * Payload publicado en /topic/matches/{matchId} cuando cambia el marcador
 * o estado de un partido. Desacoplado de la entidad Match de dominio.
 */
public record MatchUpdateMessage(
    Long matchId,
    MatchStatus status,
    Integer homeGoals,
    Integer awayGoals,
    Integer homePenalties,
    Integer awayPenalties,
    boolean wentToPenalties,
    Instant updatedAt
) {
    /**
     * Factory method que construye el mensaje a partir de una entidad Match.
     */
    public static MatchUpdateMessage from(Match match) {
        return new MatchUpdateMessage(
            match.getId(),
            match.getStatus(),
            match.getHomeGoals(),
            match.getAwayGoals(),
            match.getHomePenalties(),
            match.getAwayPenalties(),
            match.isWentToPenalties(),
            match.getUpdatedAt()
        );
    }
}
