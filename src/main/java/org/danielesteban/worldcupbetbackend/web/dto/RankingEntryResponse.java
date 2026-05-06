package org.danielesteban.worldcupbetbackend.web.dto;

/** Entrada del ranking. */
public record RankingEntryResponse(
    Long userId,
    String fullName,
    Integer totalPoints,
    Integer exactCount,
    Integer winnerCount,
    Integer position
) {}
