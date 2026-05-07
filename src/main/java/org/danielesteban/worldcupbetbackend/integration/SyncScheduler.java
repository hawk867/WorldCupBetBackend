package org.danielesteban.worldcupbetbackend.integration;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.service.MatchService;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled task that synchronizes live match data from football-data.org.
 * <p>
 * Runs every 60 seconds. Only queries the external API when there are LIVE matches
 * or SCHEDULED matches with kickoff within the next 5 minutes, conserving rate limit.
 * <p>
 * Never propagates exceptions to the Spring scheduler — all errors are caught and logged.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final FootballDataClient client;
    private final MatchRepository matchRepository;
    private final MatchService matchService;
    private final StatusMapper statusMapper;

    public SyncScheduler(@Qualifier("integrationFootballDataClient") FootballDataClient client,
                         MatchRepository matchRepository,
                         MatchService matchService,
                         StatusMapper statusMapper) {
        this.client = client;
        this.matchRepository = matchRepository;
        this.matchService = matchService;
        this.statusMapper = statusMapper;
    }

    /**
     * Main sync cycle executed every 60 seconds.
     * Only performs API calls when there are active or upcoming matches.
     */
    @Scheduled(fixedDelay = 60_000)
    public void sync() {
        try {
            if (!shouldSync()) {
                log.debug("No active or upcoming matches. Skipping sync cycle.");
                return;
            }

            transitionOverdueMatches();
            syncLiveMatches();
        } catch (ExternalApiException e) {
            log.warn("Sync cycle failed: {}. Will retry next cycle.", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during sync cycle", e);
        }
    }

    /**
     * Determines whether the sync cycle should query the external API.
     *
     * @return true if there are LIVE matches or SCHEDULED matches with kickoff within 5 minutes
     */
    boolean shouldSync() {
        List<Match> liveMatches = matchRepository.findAllByStatus(MatchStatus.LIVE);
        if (!liveMatches.isEmpty()) {
            return true;
        }

        Instant fiveMinutesFromNow = Instant.now().plus(5, ChronoUnit.MINUTES);
        List<Match> upcomingMatches = matchRepository.findAllByKickoffAtBeforeAndStatus(
                fiveMinutesFromNow, MatchStatus.SCHEDULED);
        return !upcomingMatches.isEmpty();
    }

    /**
     * Transitions SCHEDULED matches whose kickoff time has passed to LIVE.
     */
    void transitionOverdueMatches() {
        List<Match> overdueMatches = matchRepository.findAllByKickoffAtBeforeAndStatus(
                Instant.now(), MatchStatus.SCHEDULED);

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
     * Compares scores and statuses, updating only when differences are detected.
     */
    void syncLiveMatches() {
        List<ExternalMatchDto> externalMatches = client.fetchMatches();
        List<Match> liveMatches = matchRepository.findAllByStatus(MatchStatus.LIVE);

        for (Match match : liveMatches) {
            Optional<ExternalMatchDto> externalOpt = externalMatches.stream()
                    .filter(ext -> ext.id().equals(match.getExternalId()))
                    .findFirst();

            if (externalOpt.isEmpty()) {
                continue;
            }

            ExternalMatchDto external = externalOpt.get();

            // Update score if there are changes
            if (hasScoreChanged(match, external)) {
                matchService.updateScore(match.getId(), external.homeGoals(), external.awayGoals());
                log.info("Updated score for match {}: {}–{}", match.getId(),
                        external.homeGoals(), external.awayGoals());
            }

            // Transition status if the API reports a different state
            MatchStatus externalStatus = statusMapper.map(external.status());
            if (externalStatus != match.getStatus()) {
                if (externalStatus == MatchStatus.FINISHED) {
                    // Ensure final score is set before transitioning
                    if (hasScoreChanged(match, external)) {
                        matchService.updateScore(match.getId(), external.homeGoals(), external.awayGoals());
                    }
                    matchService.transitionStatus(match.getId(), MatchStatus.FINISHED);
                    log.info("Transitioned match {} to FINISHED", match.getId());
                }
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
}
