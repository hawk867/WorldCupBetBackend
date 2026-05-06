package org.danielesteban.worldcupbetbackend.service;

import net.jqwik.api.*;
import net.jqwik.api.Combinators;
import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: service-layer
 * Property 15: Ordenamiento del ranking
 */
@SuppressWarnings("unused")
class RankingServicePropertyTest {

    // Property 15: Ordenamiento del ranking
    @Property(tries = 100, seed = "42")
    void rankingIsOrderedByTotalPointsDescThenExactCountDesc(
            @ForAll("userScoreLists") List<UserScore> scores) {

        Comparator<UserScore> rankingOrder = Comparator
                .comparingInt(UserScore::getTotalPoints)
                .reversed()
                .thenComparing(Comparator.comparingInt(UserScore::getExactCount).reversed())
                .thenComparingLong(UserScore::getUserId);

        List<UserScore> ranking = scores.stream()
                .sorted(rankingOrder)
                .toList();

        for (int i = 0; i < ranking.size() - 1; i++) {
            int currentPoints = ranking.get(i).getTotalPoints();
            int nextPoints = ranking.get(i + 1).getTotalPoints();
            int currentExact = ranking.get(i).getExactCount();
            int nextExact = ranking.get(i + 1).getExactCount();

            assertThat(currentPoints).isGreaterThanOrEqualTo(nextPoints);
            if (currentPoints == nextPoints) {
                assertThat(currentExact).isGreaterThanOrEqualTo(nextExact);
            }
        }
    }

    @Provide
    Arbitrary<List<UserScore>> userScoreLists() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 100000L),
                Arbitraries.integers().between(0, 50),
                Arbitraries.integers().between(0, 10),
                Arbitraries.integers().between(0, 10)
        ).as((userId, totalPoints, exactCount, winnerCount) -> UserScore.builder()
                .userId(userId)
                .totalPoints(totalPoints)
                .exactCount(exactCount)
                .winnerCount(winnerCount)
                .build()
        ).list().ofMinSize(0).ofMaxSize(20);
    }
}
