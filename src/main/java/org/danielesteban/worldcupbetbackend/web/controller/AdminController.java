package org.danielesteban.worldcupbetbackend.web.controller;

import jakarta.validation.Valid;
import org.danielesteban.worldcupbetbackend.domain.entity.AuditLog;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.service.AdminService;
import org.danielesteban.worldcupbetbackend.service.MatchService;
import org.danielesteban.worldcupbetbackend.service.ScoringService;
import org.danielesteban.worldcupbetbackend.service.dto.CsvUploadResult;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.service.event.MatchAdjustedEvent;
import org.danielesteban.worldcupbetbackend.web.dto.AdjustResultRequest;
import org.danielesteban.worldcupbetbackend.web.dto.AuditLogResponse;
import org.danielesteban.worldcupbetbackend.web.dto.CsvRowErrorResponse;
import org.danielesteban.worldcupbetbackend.web.dto.CsvUploadResultResponse;
import org.danielesteban.worldcupbetbackend.web.dto.MatchDetailResponse;
import org.danielesteban.worldcupbetbackend.web.dto.ResetPasswordRequest;
import org.danielesteban.worldcupbetbackend.web.dto.StageResponse;
import org.danielesteban.worldcupbetbackend.web.dto.TeamResponse;
import org.danielesteban.worldcupbetbackend.web.dto.TransitionStatusRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final MatchService matchService;
    private final ScoringService scoringService;

    public AdminController(AdminService adminService,
                           MatchService matchService,
                           ScoringService scoringService) {
        this.adminService = adminService;
        this.matchService = matchService;
        this.scoringService = scoringService;
    }

    @PostMapping("/users/upload")
    public ResponseEntity<CsvUploadResultResponse> uploadUsers(
            @AuthenticationPrincipal JwtClaims principal,
            @RequestParam("file") MultipartFile file) throws IOException {
        CsvUploadResult result = adminService.uploadUsers(
                principal.userId(), file.getInputStream());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal JwtClaims principal,
            @PathVariable Long userId,
            @RequestBody @Valid ResetPasswordRequest request) {
        adminService.resetPassword(principal.userId(), userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/matches/{matchId}/result")
    public ResponseEntity<MatchDetailResponse> adjustResult(
            @AuthenticationPrincipal JwtClaims principal,
            @PathVariable Long matchId,
            @RequestBody @Valid AdjustResultRequest request) {
        Match match = adminService.adjustResult(
                principal.userId(), matchId,
                request.homeGoals(), request.awayGoals(),
                request.homePenalties(), request.awayPenalties());
        return ResponseEntity.ok(toDetailResponse(match));
    }

    @PutMapping("/matches/{matchId}/status")
    public ResponseEntity<MatchDetailResponse> transitionStatus(
            @AuthenticationPrincipal JwtClaims principal,
            @PathVariable Long matchId,
            @RequestBody @Valid TransitionStatusRequest request) {
        Match match = matchService.transitionStatus(matchId, request.status());
        return ResponseEntity.ok(toDetailResponse(match));
    }

    @PostMapping("/matches/{matchId}/recalculate")
    public ResponseEntity<Void> recalculate(@PathVariable Long matchId) {
        matchService.findById(matchId);
        scoringService.recalculateScores(new MatchAdjustedEvent(matchId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditLogResponse>> getAuditLog(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) Long entityId) {
        List<AuditLog> logs;
        if (entity != null && entityId != null) {
            logs = adminService.getAuditLogByEntity(entity, entityId);
        } else {
            logs = adminService.getAuditLog();
        }
        return ResponseEntity.ok(logs.stream().map(this::toResponse).toList());
    }

    private CsvUploadResultResponse toResponse(CsvUploadResult result) {
        List<CsvRowErrorResponse> errors = result.errors().stream()
                .map(e -> new CsvRowErrorResponse(e.rowNumber(), e.email(), e.reason()))
                .toList();
        return new CsvUploadResultResponse(result.createdCount(), errors);
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

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAdmin().getEmail(),
                log.getAction(),
                log.getEntity(),
                log.getEntityId(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
