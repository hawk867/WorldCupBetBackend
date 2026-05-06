package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scheduled service that synchronizes match data from the external football-data.org API.
 * <p>
 * Runs every 60 seconds and only performs work when there are LIVE matches or
 * SCHEDULED matches whose kickoff time has already passed. Catches all exceptions
 * internally to avoid disrupting the Spring scheduler.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /**
     * Mapping from football-data.org status strings to internal {@link MatchStatus}.
     * Statuses not present in this map are ignored during sync.
     */
    private static final Map<String, MatchStatus> STATUS_MAP = Map.of(
            "SCHEDULED", MatchStatus.SCHEDULED,
            "TIMED", MatchStatus.SCHEDULED,
            "IN_PLAY", MatchStatus.LIVE,
            "PAUSED", MatchStatus.LIVE,
            "FINISHED", MatchStatus.FINISHED,
            "POSTPONED", MatchStatus.POSTPONED,
            "SUSPENDED", MatchStatus.SUSPENDED,
            "CANCELLED", MatchStatus.CANCELLED
    );

    private final MatchRepository matchRepository;
    private final MatchService matchService;
    private final FootballDataClient footballDataClient;

    public SyncService(MatchRepository matchRepository,
                       MatchService matchService,
                       FootballDataClient footballDataClient) {
        this.matchRepository = matchRepository;
        this.matchService = matchService;
        this.footballDataClient = footballDataClient;
    }

    /**
     * Main synchronization cycle executed every 60 seconds.
     * Only processes if there are LIVE matches or SCHEDULED matches with kickoff in the past.
     */
    @Scheduled(fixedDelay = 60_000)
    public void syncMatches() {
        try {
            List<Match> liveMatches = matchRepository.findAllByStatus(MatchStatus.LIVE);
            List<Match> overdueMatches = matchRepository.findAllByKickoffAtBeforeAndStatus(
                    Instant.now(), MatchStatus.SCHEDULED);

            if (liveMatches.isEmpty() && overdueMatches.isEmpty()) {
                return;
            }

            transitionOverdueMatches(overdueMatches);
            syncLiveMatches();
        } catch (ExternalApiException e) {
            log.warn("Sync cycle failed: {}. Will retry next cycle.", e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error during sync cycle: {}. Will retry next cycle.", e.getMessage(), e);
        }
    }

    /**
     * Transitions SCHEDULED matches whose kickoffAt has passed to LIVE.
     */
    void transitionOverdueMatches() {
        List<Match> overdueMatches = matchRepository.findAllByKickoffAtBeforeAndStatus(
                Instant.now(), MatchStatus.SCHEDULED);
        transitionOverdueMatches(overdueMatches);
    }

    private void transitionOverdueMatches(List<Match> overdueMatches) {
        for (Match match : overdueMatches) {
            try {
                matchService.transitionStatus(match.getId(), MatchStatus.LIVE);
                log.info("Transitioned overdue match {} to LIVE", match.getId());
            } catch (Exception e) {
                log.warn("Failed to transition match {} to LIVE: {}", match.getId(), e.getMessage());
            }
        }
    }

    /**
     * Synchronizes live matches with data from the external API.
     * Compares scores and updates if there are changes.
     * Transitions to FINISHED if the API reports the match as finished.
     */
    void syncLiveMatches() {
        List<ExternalMatchDto> externalMatches = footballDataClient.fetchMatches();
        List<Match> liveMatches = matchRepository.findAllByStatus(MatchStatus.LIVE);

        for (Match match : liveMatches) {
            Optional<ExternalMatchDto> externalOpt = externalMatches.stream()
                    .filter(ext -> ext.id().equals(match.getExternalId()))
                    .findFirst();

            if (externalOpt.isEmpty()) {
                continue;
            }

            ExternalMatchDto external = externalOpt.get();
            MatchStatus externalStatus = mapExternalStatus(external.status());

            // Update score if there are changes
            if (hasScoreChanged(match, external)) {
                matchService.updateScore(match.getId(), external.homeGoals(), external.awayGoals());
                log.info("Updated score for match {}: {}–{}", match.getId(),
                        external.homeGoals(), external.awayGoals());
            }

            // Transition to FINISHED if the API reports it
            if (externalStatus == MatchStatus.FINISHED && match.getStatus() == MatchStatus.LIVE) {
                // Ensure final score is set before transitioning
                if (hasScoreChanged(match, external)) {
                    matchService.updateScore(match.getId(), external.homeGoals(), external.awayGoals());
                }
                matchService.transitionStatus(match.getId(), MatchStatus.FINISHED);
                log.info("Transitioned match {} to FINISHED", match.getId());
            }
        }
    }

    private boolean hasScoreChanged(Match match, ExternalMatchDto external) {
        if (external.homeGoals() == null || external.awayGoals() == null) {
            return false;
        }
        return !external.homeGoals().equals(match.getHomeGoals())
                || !external.awayGoals().equals(match.getAwayGoals());
    }

    private MatchStatus mapExternalStatus(String externalStatus) {
        if (externalStatus == null) {
            return null;
        }
        return STATUS_MAP.get(externalStatus);
    }
}
