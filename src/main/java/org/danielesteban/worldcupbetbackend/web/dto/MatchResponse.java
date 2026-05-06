package org.danielesteban.worldcupbetbackend.web.dto;

import java.time.Instant;

import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;

/** Partido en listado. */
public record MatchResponse(
    Long id,
    String stage,
    String homeTeam,
    String homeTeamCode,
    String homeTeamFlagUrl,
    String awayTeam,
    String awayTeamCode,
    String awayTeamFlagUrl,
    Instant kickoffAt,
    MatchStatus status,
    Integer homeGoals,
    Integer awayGoals,
    Integer homePenalties,
    Integer awayPenalties,
    boolean wentToPenalties
) {}
