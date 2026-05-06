package org.danielesteban.worldcupbetbackend.web.dto;

import jakarta.validation.constraints.NotNull;

/** PUT /api/admin/matches/{matchId}/result */
public record AdjustResultRequest(
    @NotNull Integer homeGoals,
    @NotNull Integer awayGoals,
    Integer homePenalties,
    Integer awayPenalties
) {}
