package org.danielesteban.worldcupbetbackend.web.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/predictions */
public record CreatePredictionRequest(
    @NotNull Long matchId,
    @NotNull Integer homeGoals,
    @NotNull Integer awayGoals,
    Integer homePenalties,
    Integer awayPenalties
) {}
