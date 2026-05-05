package org.danielesteban.worldcupbetbackend.service.dto;

public record ScoreBreakdown(int exactScorePoints, int winnerPoints, int penaltiesPoints) {
    public int total() {
        return exactScorePoints + winnerPoints + penaltiesPoints;
    }
}
