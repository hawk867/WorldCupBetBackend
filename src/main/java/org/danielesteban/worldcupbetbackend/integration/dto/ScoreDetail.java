package org.danielesteban.worldcupbetbackend.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreDetail(Integer home, Integer away) {}
