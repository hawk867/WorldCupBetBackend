package org.danielesteban.worldcupbetbackend.web.dto;

import java.time.Instant;

/** Predicción del usuario. */
public record PredictionResponse(
    Long id,
    Long matchId,
    String homeTeam,
    String awayTeam,
    Instant kickoffAt,
    Integer homeGoals,
    Integer awayGoals,
    Integer homePenalties,
    Integer awayPenalties,
    Instant createdAt,
    Instant updatedAt
) {}
