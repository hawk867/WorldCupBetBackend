package org.danielesteban.worldcupbetbackend.websocket.dto;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RankingUpdateMessagePropertyTest {

    // Feature: websocket-realtime, Property 4: Mapeo correcto de UserScore a RankingUpdateMessage
    @Property(tries = 100)
    void fromRankingPreservesAllFieldsAndSize(@ForAll("arbitraryRankings") List<UserScore> ranking) {
        // **Validates: Requirements 4.3, 9.4**
        RankingUpdateMessage message = RankingUpdateMessage.from(ranking);

        assertThat(message.entries()).hasSize(ranking.size());

        for (int i = 0; i < ranking.size(); i++) {
            UserScore us = ranking.get(i);
            RankingEntry entry = message.entries().get(i);

            assertThat(entry.userId()).isEqualTo(us.getUserId());
            assertThat(entry.fullName()).isEqualTo(us.getUser().getFullName());
            assertThat(entry.totalPoints()).isEqualTo(us.getTotalPoints());
            assertThat(entry.exactCount()).isEqualTo(us.getExactCount());
            assertThat(entry.winnerCount()).isEqualTo(us.getWinnerCount());
        }
    }

    // Feature: websocket-realtime, Property 5: Ordenamiento y posición del ranking publicado
    @Property(tries = 100)
    void positionsAreSequentialOneToN(@ForAll("orderedRankings") List<UserScore> ranking) {
        // **Validates: Requirements 4.4, 8.4**
        RankingUpdateMessage message = RankingUpdateMessage.from(ranking);

        List<RankingEntry> entries = message.entries();

        // Positions must be sequential 1..N
        for (int i = 0; i < entries.size(); i++) {
            assertThat(entries.get(i).position()).isEqualTo(i + 1);
        }

        // Order must be maintained (totalPoints DESC, exactCount DESC as tiebreaker)
        for (int i = 0; i < entries.size() - 1; i++) {
            RankingEntry current = entries.get(i);
            RankingEntry next = entries.get(i + 1);
            assertThat(current.totalPoints()).isGreaterThanOrEqualTo(next.totalPoints());
            if (current.totalPoints().equals(next.totalPoints())) {
                assertThat(current.exactCount()).isGreaterThanOrEqualTo(next.exactCount());
            }
        }
    }

    @Provide
    Arbitrary<List<UserScore>> arbitraryRankings() {
        return Arbitraries.integers().between(1, 30).flatMap(size ->
            Arbitraries.integers().between(0, 100).list().ofSize(size).flatMap(points ->
                Arbitraries.integers().between(0, 20).list().ofSize(size).flatMap(exacts ->
                    Arbitraries.integers().between(0, 20).list().ofSize(size).flatMap(winners ->
                        Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20).list().ofSize(size).map(names ->
                            IntStream.range(0, size).mapToObj(i -> {
                                User user = User.builder()
                                    .id((long) (i + 1))
                                    .fullName(names.get(i))
                                    .build();
                                return UserScore.builder()
                                    .userId((long) (i + 1))
                                    .user(user)
                                    .totalPoints(points.get(i))
                                    .exactCount(exacts.get(i))
                                    .winnerCount(winners.get(i))
                                    .build();
                            }).toList()
                        )
                    )
                )
            )
        );
    }

    @Provide
    Arbitrary<List<UserScore>> orderedRankings() {
        return arbitraryRankings().map(ranking ->
            ranking.stream()
                .sorted(Comparator.comparingInt(UserScore::getTotalPoints).reversed()
                    .thenComparing(Comparator.comparingInt(UserScore::getExactCount).reversed()))
                .toList()
        );
    }
}
