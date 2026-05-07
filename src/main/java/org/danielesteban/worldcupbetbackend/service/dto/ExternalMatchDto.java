package org.danielesteban.worldcupbetbackend.service.dto;

import java.time.Instant;

public record ExternalMatchDto(
        Integer id,
        String status,
        Integer homeGoals,
        Integer awayGoals,
        Integer homePenalties,
        Integer awayPenalties,
        Integer homeTeamExternalId,
        Integer awayTeamExternalId,
        String stageName,
        Instant kickoffAt
) {}
