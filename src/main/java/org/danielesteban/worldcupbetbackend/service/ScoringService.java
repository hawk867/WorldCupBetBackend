package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.domain.entity.PredictionScore;
import org.danielesteban.worldcupbetbackend.persistence.repository.PredictionRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.PredictionScoreRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserScoreRepository;
import org.danielesteban.worldcupbetbackend.service.dto.ScoreBreakdown;
import org.danielesteban.worldcupbetbackend.service.event.MatchAdjustedEvent;
import org.danielesteban.worldcupbetbackend.service.event.MatchFinishedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

@Service
public class ScoringService {

    private final PredictionRepository predictionRepository;
    private final PredictionScoreRepository predictionScoreRepository;
    private final UserScoreRepository userScoreRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ScoringService(PredictionRepository predictionRepository,
                          PredictionScoreRepository predictionScoreRepository,
                          UserScoreRepository userScoreRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.predictionRepository = predictionRepository;
        this.predictionScoreRepository = predictionScoreRepository;
        this.userScoreRepository = userScoreRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public ScoreBreakdown computeScore(Prediction prediction, Match match) {
        int exactScorePoints = 0;
        int winnerPoints = 0;
        int penaltiesPoints = 0;

        if (prediction.getHomeGoals().equals(match.getHomeGoals())
                && prediction.getAwayGoals().equals(match.getAwayGoals())) {
            exactScorePoints = 4;
        } else if (winner(prediction.getHomeGoals(), prediction.getAwayGoals())
                == winner(match.getHomeGoals(), match.getAwayGoals())) {
            winnerPoints = 2;
        }

        boolean isKnockout = match.getStage().getOrderIdx() > 1;
        if (match.isWentToPenalties() && isKnockout
                && prediction.getHomePenalties() != null && prediction.getAwayPenalties() != null) {
            if (prediction.getHomePenalties().equals(match.getHomePenalties())
                    && prediction.getAwayPenalties().equals(match.getAwayPenalties())) {
                penaltiesPoints = 3;
            } else if (penaltyWinner(prediction.getHomePenalties(), prediction.getAwayPenalties())
                    == penaltyWinner(match.getHomePenalties(), match.getAwayPenalties())) {
                penaltiesPoints = 1;
            }
        }

        return new ScoreBreakdown(exactScorePoints, winnerPoints, penaltiesPoints);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void calculateScores(MatchFinishedEvent event) {
        scoreMatch(event.matchId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recalculateScores(MatchAdjustedEvent event) {
        Long matchId = event.matchId();
        List<Prediction> predictions = predictionRepository.findAllByMatchId(matchId);

        for (Prediction prediction : predictions) {
            predictionScoreRepository.deleteByPredictionId(prediction.getId());
        }

        scoreMatch(matchId);

        List<Long> userIds = predictions.stream()
                .map(p -> p.getUser().getId())
                .distinct()
                .toList();

        for (Long userId : userIds) {
            userScoreRepository.findById(userId).ifPresent(userScore -> {
                int total = predictionScoreRepository.sumPointsByUserId(userId);
                userScore.setTotalPoints(total);
                userScore.setExactCount(countExact(userId));
                userScore.setWinnerCount(countWinner(userId));
            });
        }

        publishRanking();
    }

    private void scoreMatch(Long matchId) {
        List<Prediction> predictions = predictionRepository.findAllByMatchId(matchId);
        if (predictions.isEmpty()) return;

        Match match = predictions.getFirst().getMatch();

        for (Prediction prediction : predictions) {
            ScoreBreakdown breakdown = computeScore(prediction, match);

            predictionScoreRepository.save(PredictionScore.builder()
                    .prediction(prediction)
                    .points(breakdown.total())
                    .exactScorePoints(breakdown.exactScorePoints())
                    .winnerPoints(breakdown.winnerPoints())
                    .penaltiesPoints(breakdown.penaltiesPoints())
                    .calculatedAt(Instant.now())
                    .build());

            userScoreRepository.findById(prediction.getUser().getId()).ifPresent(userScore -> {
                userScore.setTotalPoints(userScore.getTotalPoints() + breakdown.total());
                if (breakdown.exactScorePoints() > 0) {
                    userScore.setExactCount(userScore.getExactCount() + 1);
                } else if (breakdown.winnerPoints() > 0) {
                    userScore.setWinnerCount(userScore.getWinnerCount() + 1);
                }
            });
        }

        publishRanking();
    }

    private void publishRanking() {
        messagingTemplate.convertAndSend("/topic/ranking",
                userScoreRepository.findAllByOrderByTotalPointsDescExactCountDesc());
    }

    private int countExact(Long userId) {
        return (int) predictionRepository.findAllByUserId(userId).stream()
                .filter(p -> predictionScoreRepository.findByPredictionId(p.getId())
                        .map(ps -> ps.getExactScorePoints() > 0)
                        .orElse(false))
                .count();
    }

    private int countWinner(Long userId) {
        return (int) predictionRepository.findAllByUserId(userId).stream()
                .filter(p -> predictionScoreRepository.findByPredictionId(p.getId())
                        .map(ps -> ps.getWinnerPoints() > 0)
                        .orElse(false))
                .count();
    }

    private enum Result { HOME, AWAY, DRAW }

    private Result winner(int home, int away) {
        if (home > away) return Result.HOME;
        if (away > home) return Result.AWAY;
        return Result.DRAW;
    }

    private Result penaltyWinner(int home, int away) {
        return home > away ? Result.HOME : Result.AWAY;
    }
}
