package org.danielesteban.worldcupbetbackend.web.controller;

import jakarta.validation.Valid;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.service.PredictionService;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.web.dto.CreatePredictionRequest;
import org.danielesteban.worldcupbetbackend.web.dto.PredictionResponse;
import org.danielesteban.worldcupbetbackend.web.dto.UpdatePredictionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping
    public ResponseEntity<PredictionResponse> create(
            @AuthenticationPrincipal JwtClaims principal,
            @RequestBody @Valid CreatePredictionRequest request) {
        Prediction prediction = predictionService.create(
                principal.userId(), request.matchId(),
                request.homeGoals(), request.awayGoals(),
                request.homePenalties(), request.awayPenalties());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(prediction));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PredictionResponse> update(
            @AuthenticationPrincipal JwtClaims principal,
            @PathVariable Long id,
            @RequestBody @Valid UpdatePredictionRequest request) {
        Prediction prediction = predictionService.update(
                principal.userId(), id,
                request.homeGoals(), request.awayGoals(),
                request.homePenalties(), request.awayPenalties());
        return ResponseEntity.ok(toResponse(prediction));
    }

    @GetMapping
    public ResponseEntity<List<PredictionResponse>> getMyPredictions(
            @AuthenticationPrincipal JwtClaims principal) {
        List<Prediction> predictions = predictionService.findByUser(principal.userId());
        return ResponseEntity.ok(predictions.stream().map(this::toResponse).toList());
    }

    @GetMapping("/match/{matchId}")
    public ResponseEntity<PredictionResponse> getByMatch(
            @AuthenticationPrincipal JwtClaims principal,
            @PathVariable Long matchId) {
        return predictionService.findByUserAndMatch(principal.userId(), matchId)
                .map(p -> ResponseEntity.ok(toResponse(p)))
                .orElse(ResponseEntity.noContent().build());
    }

    private PredictionResponse toResponse(Prediction prediction) {
        return new PredictionResponse(
                prediction.getId(),
                prediction.getMatch().getId(),
                prediction.getMatch().getHomeTeam().getName(),
                prediction.getMatch().getAwayTeam().getName(),
                prediction.getMatch().getKickoffAt(),
                prediction.getHomeGoals(),
                prediction.getAwayGoals(),
                prediction.getHomePenalties(),
                prediction.getAwayPenalties(),
                prediction.getCreatedAt(),
                prediction.getUpdatedAt()
        );
    }
}
