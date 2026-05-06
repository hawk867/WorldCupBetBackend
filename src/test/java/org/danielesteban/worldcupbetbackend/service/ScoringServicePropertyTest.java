package org.danielesteban.worldcupbetbackend.service;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.*;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.persistence.repository.PredictionRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.PredictionScoreRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserScoreRepository;
import org.danielesteban.worldcupbetbackend.service.dto.ScoreBreakdown;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Feature: service-layer
 * Properties 12-14: ScoringService correctness properties
 */
@SuppressWarnings("unused")
class ScoringServicePropertyTest {

    private final ScoringService scoringService = new ScoringService(
            mock(PredictionRepository.class),
            mock(PredictionScoreRepository.class),
            mock(UserScoreRepository.class),
            mock(SimpMessagingTemplate.class)
    );

    private Match matchWithScore(int homeGoals, int awayGoals, boolean wentToPenalties,
                                 Integer homePen, Integer awayPen, int stageOrderIdx) {
        Stage stage = Stage.builder().id(1L).name("S").orderIdx(stageOrderIdx).build();
        return Match.builder()
                .id(1L).status(MatchStatus.FINISHED).stage(stage)
                .homeGoals(homeGoals).awayGoals(awayGoals)
                .wentToPenalties(wentToPenalties)
                .homePenalties(homePen).awayPenalties(awayPen)
                .build();
    }

    private Prediction predictionWithScore(int homeGoals, int awayGoals,
                                           Integer homePen, Integer awayPen) {
        User user = User.builder().id(1L).email("a@a.com").passwordHash("x")
                .fullName("A").role(UserRole.USER).build();
        return Prediction.builder()
                .id(1L).user(user)
                .homeGoals(homeGoals).awayGoals(awayGoals)
                .homePenalties(homePen).awayPenalties(awayPen)
                .build();
    }

    // Property 12: Reglas de puntuación (goles regulares)
    @Property(tries = 200)
    void scoringRulesForRegularGoals(
            @ForAll("validGoals") int predHome, @ForAll("validGoals") int predAway,
            @ForAll("validGoals") int matchHome, @ForAll("validGoals") int matchAway) {

        Prediction prediction = predictionWithScore(predHome, predAway, null, null);
        Match match = matchWithScore(matchHome, matchAway, false, null, null, 1);

        ScoreBreakdown breakdown = scoringService.computeScore(prediction, match);

        if (predHome == matchHome && predAway == matchAway) {
            assertThat(breakdown.exactScorePoints()).isEqualTo(4);
            assertThat(breakdown.winnerPoints()).isEqualTo(0);
        } else if (sameWinner(predHome, predAway, matchHome, matchAway)) {
            assertThat(breakdown.exactScorePoints()).isEqualTo(0);
            assertThat(breakdown.winnerPoints()).isEqualTo(2);
        } else {
            assertThat(breakdown.exactScorePoints()).isEqualTo(0);
            assertThat(breakdown.winnerPoints()).isEqualTo(0);
        }
    }

    // Property 13: Reglas de puntuación (penaltis)
    @Property(tries = 200)
    void scoringRulesForPenalties(
            @ForAll("validGoals") int goals,
            @ForAll("differentPenalties") int[] predPen,
            @ForAll("differentPenalties") int[] matchPen) {

        Prediction prediction = predictionWithScore(goals, goals, predPen[0], predPen[1]);
        Match match = matchWithScore(goals, goals, true, matchPen[0], matchPen[1], 2);

        ScoreBreakdown breakdown = scoringService.computeScore(prediction, match);

        if (predPen[0] == matchPen[0] && predPen[1] == matchPen[1]) {
            assertThat(breakdown.penaltiesPoints()).isEqualTo(3);
        } else if (penaltyWinner(predPen[0], predPen[1]) == penaltyWinner(matchPen[0], matchPen[1])) {
            assertThat(breakdown.penaltiesPoints()).isEqualTo(1);
        } else {
            assertThat(breakdown.penaltiesPoints()).isEqualTo(0);
        }
    }

    // Property 14: Invariante de UserScore (suma de PredictionScores)
    @Property(tries = 100)
    void userScoreTotalEqualsSum(
            @ForAll("scoreLists") List<Integer> scores) {

        int expectedTotal = scores.stream().mapToInt(Integer::intValue).sum();

        UserScore userScore = UserScore.builder()
                .userId(1L).totalPoints(0).exactCount(0).winnerCount(0).build();

        for (int points : scores) {
            userScore.setTotalPoints(userScore.getTotalPoints() + points);
        }

        assertThat(userScore.getTotalPoints()).isEqualTo(expectedTotal);
    }

    private boolean sameWinner(int ph, int pa, int mh, int ma) {
        return Integer.signum(ph - pa) == Integer.signum(mh - ma);
    }

    private boolean penaltyWinner(int home, int away) {
        return home > away;
    }

    @Provide
    Arbitrary<Integer> validGoals() {
        return Arbitraries.integers().between(0, 15);
    }

    @Provide
    Arbitrary<int[]> differentPenalties() {
        return Arbitraries.integers().between(0, 10)
                .flatMap(a -> Arbitraries.integers().between(0, 10)
                        .filter(b -> !b.equals(a))
                        .map(b -> new int[]{a, b}));
    }

    @Provide
    Arbitrary<List<Integer>> scoreLists() {
        return Arbitraries.integers().between(0, 7)
                .list().ofMinSize(0).ofMaxSize(20);
    }
}
