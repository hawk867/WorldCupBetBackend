package org.danielesteban.worldcupbetbackend.web.dto;

import jakarta.validation.constraints.NotNull;

/** PUT /api/predictions/{id} */
public record UpdatePredictionRequest(
    @NotNull Integer homeGoals,
    @NotNull Integer awayGoals,
    Integer homePenalties,
    Integer awayPenalties
) {}
