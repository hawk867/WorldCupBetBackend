package org.danielesteban.worldcupbetbackend.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitionMatchesResponse(List<MatchResponse> matches) {}
