package org.danielesteban.worldcupbetbackend.web.controller;

import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.danielesteban.worldcupbetbackend.service.RankingService;
import org.danielesteban.worldcupbetbackend.web.dto.RankingEntryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping
    public ResponseEntity<List<RankingEntryResponse>> getRanking() {
        List<UserScore> ranking = rankingService.getRanking();
        AtomicInteger position = new AtomicInteger(1);
        List<RankingEntryResponse> response = ranking.stream()
                .map(us -> toResponse(us, position.getAndIncrement()))
                .toList();
        return ResponseEntity.ok(response);
    }

    private RankingEntryResponse toResponse(UserScore userScore, int position) {
        return new RankingEntryResponse(
                userScore.getUserId(),
                userScore.getUser().getFullName(),
                userScore.getTotalPoints(),
                userScore.getExactCount(),
                userScore.getWinnerCount(),
                position
        );
    }
}
