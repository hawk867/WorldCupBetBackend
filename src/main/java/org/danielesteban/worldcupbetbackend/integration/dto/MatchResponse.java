package org.danielesteban.worldcupbetbackend.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchResponse(
        Integer id,
        String utcDate,
        String status,
        String stage,
        MatchScoreResponse score,
        MatchTeamResponse homeTeam,
        MatchTeamResponse awayTeam
) {}
