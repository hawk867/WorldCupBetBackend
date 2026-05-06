package org.danielesteban.worldcupbetbackend.web.dto;

import jakarta.validation.constraints.NotNull;

import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;

/** PUT /api/admin/matches/{matchId}/status */
public record TransitionStatusRequest(
    @NotNull MatchStatus status
) {}
