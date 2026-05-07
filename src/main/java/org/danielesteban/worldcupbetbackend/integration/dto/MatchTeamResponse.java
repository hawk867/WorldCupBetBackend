package org.danielesteban.worldcupbetbackend.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchTeamResponse(Integer id, String name, String tla, String crest) {}
