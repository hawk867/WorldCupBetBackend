package org.danielesteban.worldcupbetbackend.websocket.dto;

/**
 * Una entrada individual del ranking publicado vía WebSocket.
 */
public record RankingEntry(
    Long userId,
    String fullName,
    Integer totalPoints,
    Integer exactCount,
    Integer winnerCount,
    Integer position
) {}
