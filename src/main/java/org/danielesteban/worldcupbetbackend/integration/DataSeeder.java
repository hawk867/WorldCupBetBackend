package org.danielesteban.worldcupbetbackend.integration;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.StageRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.TeamRepository;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalTeamDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seeds teams and matches from the football-data.org API into the local database.
 * <p>
 * Idempotent: can be executed multiple times without creating duplicates.
 * Uses externalId as the upsert key for both teams and matches.
 */
@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final Map<String, String> STAGE_NAME_MAPPING = Map.ofEntries(
            Map.entry("GROUP_STAGE", "GROUP_STAGE"),
            Map.entry("LAST_16", "LAST_16"),
            Map.entry("QUARTER_FINALS", "QUARTER_FINALS"),
            Map.entry("SEMI_FINALS", "SEMI_FINALS"),
            Map.entry("THIRD_PLACE", "THIRD_PLACE"),
            Map.entry("FINAL", "FINAL")
    );

    private final FootballDataClient client;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final StageRepository stageRepository;
    private final StatusMapper statusMapper;

    public DataSeeder(@Qualifier("integrationFootballDataClient") FootballDataClient client,
                      TeamRepository teamRepository,
                      MatchRepository matchRepository,
                      StageRepository stageRepository,
                      StatusMapper statusMapper) {
        this.client = client;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.stageRepository = stageRepository;
        this.statusMapper = statusMapper;
    }

    /**
     * Executes the full seed: teams → matches.
     * Idempotent: can be run multiple times without duplicates.
     *
     * @return combined SeedResult with totals from both phases
     */
    @Transactional
    public SeedResult seedAll() {
        SeedResult teamsResult = seedTeams();
        SeedResult matchesResult = seedMatches();

        SeedResult combined = new SeedResult(
                teamsResult.created() + matchesResult.created(),
                teamsResult.updated() + matchesResult.updated(),
                teamsResult.skipped() + matchesResult.skipped()
        );

        log.info("Seed completed: {} teams created, {} updated. {} matches created, {} updated, {} skipped.",
                teamsResult.created(), teamsResult.updated(),
                matchesResult.created(), matchesResult.updated(), matchesResult.skipped());

        return combined;
    }

    /**
     * Imports/updates teams from the external API.
     */
    @Transactional
    public SeedResult seedTeams() {
        List<ExternalTeamDto> externalTeams = client.fetchTeams();

        int created = 0;
        int updated = 0;

        for (ExternalTeamDto ext : externalTeams) {
            Optional<Team> existingOpt = teamRepository.findByExternalId(ext.id());

            if (existingOpt.isPresent()) {
                Team existing = existingOpt.get();
                existing.setName(ext.name());
                existing.setCode(ext.tla());
                existing.setFlagUrl(ext.crest());
                teamRepository.save(existing);
                updated++;
            } else {
                Team newTeam = Team.builder()
                        .externalId(ext.id())
                        .name(ext.name())
                        .code(ext.tla())
                        .flagUrl(ext.crest())
                        .build();
                teamRepository.save(newTeam);
                created++;
            }
        }

        log.info("Teams seed: {} created, {} updated", created, updated);
        return new SeedResult(created, updated, 0);
    }

    /**
     * Imports/updates matches from the external API.
     * Requires teams to already exist in the database.
     */
    @Transactional
    public SeedResult seedMatches() {
        List<ExternalMatchDto> externalMatches = client.fetchMatches();

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (ExternalMatchDto ext : externalMatches) {
            // Resolve stage
            String internalStageName = STAGE_NAME_MAPPING.get(ext.stageName());
            if (internalStageName == null) {
                log.warn("Unknown stage name '{}' for match externalId={}. Skipping.", ext.stageName(), ext.id());
                skipped++;
                continue;
            }

            Optional<Stage> stageOpt = stageRepository.findByName(internalStageName);
            if (stageOpt.isEmpty()) {
                log.warn("Stage '{}' not found in database for match externalId={}. Skipping.", internalStageName, ext.id());
                skipped++;
                continue;
            }

            // Resolve teams
            Optional<Team> homeTeamOpt = teamRepository.findByExternalId(ext.homeTeamExternalId());
            if (homeTeamOpt.isEmpty()) {
                log.warn("Home team with externalId={} not found for match externalId={}. Skipping.",
                        ext.homeTeamExternalId(), ext.id());
                skipped++;
                continue;
            }

            Optional<Team> awayTeamOpt = teamRepository.findByExternalId(ext.awayTeamExternalId());
            if (awayTeamOpt.isEmpty()) {
                log.warn("Away team with externalId={} not found for match externalId={}. Skipping.",
                        ext.awayTeamExternalId(), ext.id());
                skipped++;
                continue;
            }

            Stage stage = stageOpt.get();
            Team homeTeam = homeTeamOpt.get();
            Team awayTeam = awayTeamOpt.get();
            MatchStatus status = statusMapper.map(ext.status());

            boolean hasPenalties = ext.homePenalties() != null && ext.awayPenalties() != null;

            Optional<Match> existingOpt = matchRepository.findByExternalId(ext.id());

            if (existingOpt.isPresent()) {
                Match existing = existingOpt.get();
                existing.setStage(stage);
                existing.setHomeTeam(homeTeam);
                existing.setAwayTeam(awayTeam);
                existing.setKickoffAt(ext.kickoffAt());
                existing.setStatus(status);
                existing.setHomeGoals(ext.homeGoals());
                existing.setAwayGoals(ext.awayGoals());
                existing.setHomePenalties(ext.homePenalties());
                existing.setAwayPenalties(ext.awayPenalties());
                existing.setWentToPenalties(hasPenalties);
                matchRepository.save(existing);
                updated++;
            } else {
                Match newMatch = Match.builder()
                        .externalId(ext.id())
                        .stage(stage)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .kickoffAt(ext.kickoffAt())
                        .status(status)
                        .homeGoals(ext.homeGoals())
                        .awayGoals(ext.awayGoals())
                        .homePenalties(ext.homePenalties())
                        .awayPenalties(ext.awayPenalties())
                        .wentToPenalties(hasPenalties)
                        .build();
                matchRepository.save(newMatch);
                created++;
            }
        }

        log.info("Matches seed: {} created, {} updated, {} skipped", created, updated, skipped);
        return new SeedResult(created, updated, skipped);
    }

    /**
     * Result of a seed operation.
     */
    public record SeedResult(int created, int updated, int skipped) {}
}
