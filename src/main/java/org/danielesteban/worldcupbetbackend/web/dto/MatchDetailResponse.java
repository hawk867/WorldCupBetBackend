package org.danielesteban.worldcupbetbackend.web.dto;

import java.time.Instant;

import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;

/** Partido con detalle completo. */
public record MatchDetailResponse(
    Long id,
    StageResponse stage,
    TeamResponse homeTeam,
    TeamResponse awayTeam,
    Instant kickoffAt,
    MatchStatus status,
    Integer homeGoals,
    Integer awayGoals,
    Integer homePenalties,
    Integer awayPenalties,
    boolean wentToPenalties,
    Instant updatedAt
) {}
