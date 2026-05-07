package org.danielesteban.worldcupbetbackend.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchScoreResponse(
        String winner,
        String duration,
        ScoreDetail fullTime,
        ScoreDetail halfTime,
        ScoreDetail penalties
) {}
