package org.danielesteban.worldcupbetbackend.web.controller;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.service.MatchService;
import org.danielesteban.worldcupbetbackend.web.dto.MatchDetailResponse;
import org.danielesteban.worldcupbetbackend.web.dto.MatchResponse;
import org.danielesteban.worldcupbetbackend.web.dto.StageResponse;
import org.danielesteban.worldcupbetbackend.web.dto.TeamResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getMatches(
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) MatchStatus status) {
        List<Match> matches;
        if (stageId != null) {
            matches = matchService.findByStage(stageId);
        } else if (status != null) {
            matches = matchService.findByStatus(status);
        } else {
            matches = matchService.findAll();
        }
        return ResponseEntity.ok(matches.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchDetailResponse> getMatch(@PathVariable Long id) {
        Match match = matchService.findById(id);
        return ResponseEntity.ok(toDetailResponse(match));
    }

    private MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getStage().getName(),
                match.getHomeTeam().getName(),
                match.getHomeTeam().getCode(),
                match.getHomeTeam().getFlagUrl(),
                match.getAwayTeam().getName(),
                match.getAwayTeam().getCode(),
                match.getAwayTeam().getFlagUrl(),
                match.getKickoffAt(),
                match.getStatus(),
                match.getHomeGoals(),
                match.getAwayGoals(),
                match.getHomePenalties(),
                match.getAwayPenalties(),
                match.isWentToPenalties()
        );
    }

    private MatchDetailResponse toDetailResponse(Match match) {
        return new MatchDetailResponse(
                match.getId(),
                new StageResponse(match.getStage().getId(),
                        match.getStage().getName(),
                        match.getStage().getOrderIdx()),
                new TeamResponse(match.getHomeTeam().getId(),
                        match.getHomeTeam().getName(),
                        match.getHomeTeam().getCode(),
                        match.getHomeTeam().getFlagUrl()),
                new TeamResponse(match.getAwayTeam().getId(),
                        match.getAwayTeam().getName(),
                        match.getAwayTeam().getCode(),
                        match.getAwayTeam().getFlagUrl()),
                match.getKickoffAt(),
                match.getStatus(),
                match.getHomeGoals(),
                match.getAwayGoals(),
                match.getHomePenalties(),
                match.getAwayPenalties(),
                match.isWentToPenalties(),
                match.getUpdatedAt()
        );
    }
}
