package org.danielesteban.worldcupbetbackend.websocket.dto;

import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Payload publicado en /topic/ranking cuando se recalcula el ranking global.
 * Contiene la lista completa de posiciones ordenada por totalPoints DESC,
 * exactCount DESC, con posición 1-based.
 */
public record RankingUpdateMessage(
    List<RankingEntry> entries
) {
    /**
     * Factory method que construye el mensaje a partir de una lista de UserScore
     * ya ordenada por el repositorio (totalPoints DESC, exactCount DESC).
     */
    public static RankingUpdateMessage from(List<UserScore> ranking) {
        AtomicInteger position = new AtomicInteger(1);
        List<RankingEntry> entries = ranking.stream()
            .map(us -> new RankingEntry(
                us.getUserId(),
                us.getUser().getFullName(),
                us.getTotalPoints(),
                us.getExactCount(),
                us.getWinnerCount(),
                position.getAndIncrement()
            ))
            .toList();
        return new RankingUpdateMessage(entries);
    }
}
