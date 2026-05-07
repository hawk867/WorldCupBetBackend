package org.danielesteban.worldcupbetbackend.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danielesteban.worldcupbetbackend.integration.dto.CompetitionMatchesResponse;
import org.danielesteban.worldcupbetbackend.integration.dto.CompetitionTeamsResponse;
import org.danielesteban.worldcupbetbackend.integration.dto.MatchResponse;
import org.danielesteban.worldcupbetbackend.integration.dto.TeamResponse;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalTeamDto;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * HTTP client for the football-data.org v4 API.
 * <p>
 * Uses the configured RestClient bean with rate limiting, retry logic,
 * and proper authentication. Converts external API DTOs to internal service DTOs.
 */
@Component("integrationFootballDataClient")
public class FootballDataClient {

    private static final Logger log = LoggerFactory.getLogger(FootballDataClient.class);

    private final RestClient restClient;
    private final FootballDataProperties properties;
    private final ObjectMapper objectMapper;

    public FootballDataClient(@Qualifier("footballDataRestClient") RestClient restClient,
                              FootballDataProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Fetches all teams for the configured competition.
     *
     * @return list of external team DTOs
     * @throws ExternalApiException if the API returns an error or the JSON is invalid
     */
    public List<ExternalTeamDto> fetchTeams() {
        String responseBody = null;
        try {
            responseBody = restClient.get()
                    .uri("/v4/competitions/{competitionId}/teams", properties.competitionId())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ExternalApiException(response.getStatusCode().value(),
                                "Failed to fetch teams: HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);

            CompetitionTeamsResponse response = objectMapper.readValue(responseBody, CompetitionTeamsResponse.class);

            if (response == null || response.teams() == null) {
                return Collections.emptyList();
            }

            return response.teams().stream()
                    .map(this::toExternalTeamDto)
                    .toList();

        } catch (JsonProcessingException e) {
            log.error("Deserialization error from football-data.org: {}. Response body (truncated): {}",
                    e.getMessage(), truncate(responseBody, 500));
            throw new ExternalApiException("Failed to deserialize teams response: " + e.getMessage(), e);
        } catch (ExternalApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ExternalApiException(e.getStatusCode().value(),
                    "Failed to fetch teams: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch teams: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches all matches for the configured competition.
     * Filters out matches with TBD teams (null homeTeam.id or awayTeam.id).
     *
     * @return list of external match DTOs
     * @throws ExternalApiException if the API returns an error or the JSON is invalid
     */
    public List<ExternalMatchDto> fetchMatches() {
        String responseBody = null;
        try {
            responseBody = restClient.get()
                    .uri("/v4/competitions/{competitionId}/matches", properties.competitionId())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ExternalApiException(response.getStatusCode().value(),
                                "Failed to fetch matches: HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);

            CompetitionMatchesResponse response = objectMapper.readValue(responseBody, CompetitionMatchesResponse.class);

            if (response == null || response.matches() == null) {
                return Collections.emptyList();
            }

            return response.matches().stream()
                    .filter(m -> m.homeTeam() != null && m.homeTeam().id() != null
                            && m.awayTeam() != null && m.awayTeam().id() != null)
                    .map(this::toExternalMatchDto)
                    .toList();

        } catch (JsonProcessingException e) {
            log.error("Deserialization error from football-data.org: {}. Response body (truncated): {}",
                    e.getMessage(), truncate(responseBody, 500));
            throw new ExternalApiException("Failed to deserialize matches response: " + e.getMessage(), e);
        } catch (ExternalApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ExternalApiException(e.getStatusCode().value(),
                    "Failed to fetch matches: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch matches: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches a single match by its external ID.
     *
     * @param externalId the football-data.org match identifier
     * @return the match DTO, or empty if not found (404)
     * @throws ExternalApiException for other HTTP errors or deserialization failures
     */
    public Optional<ExternalMatchDto> fetchMatch(Integer externalId) {
        String responseBody = null;
        try {
            responseBody = restClient.get()
                    .uri("/v4/matches/{externalId}", externalId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        // Do nothing — we handle 404 below by returning empty
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ExternalApiException(response.getStatusCode().value(),
                                "Failed to fetch match " + externalId + ": HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            MatchResponse matchResponse = objectMapper.readValue(responseBody, MatchResponse.class);

            if (matchResponse == null || matchResponse.id() == null) {
                return Optional.empty();
            }

            return Optional.of(toExternalMatchDto(matchResponse));

        } catch (JsonProcessingException e) {
            log.error("Deserialization error from football-data.org: {}. Response body (truncated): {}",
                    e.getMessage(), truncate(responseBody, 500));
            throw new ExternalApiException("Failed to deserialize match response: " + e.getMessage(), e);
        } catch (ExternalApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new ExternalApiException(e.getStatusCode().value(),
                    "Failed to fetch match " + externalId + ": HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch match " + externalId + ": " + e.getMessage(), e);
        }
    }

    private ExternalTeamDto toExternalTeamDto(TeamResponse response) {
        return new ExternalTeamDto(
                response.id(),
                response.name(),
                response.tla(),
                response.crest()
        );
    }

    ExternalMatchDto toExternalMatchDto(MatchResponse response) {
        return new ExternalMatchDto(
                response.id(),
                response.status(),
                response.score() != null && response.score().fullTime() != null
                        ? response.score().fullTime().home() : null,
                response.score() != null && response.score().fullTime() != null
                        ? response.score().fullTime().away() : null,
                response.score() != null && response.score().penalties() != null
                        ? response.score().penalties().home() : null,
                response.score() != null && response.score().penalties() != null
                        ? response.score().penalties().away() : null,
                response.homeTeam() != null ? response.homeTeam().id() : null,
                response.awayTeam() != null ? response.awayTeam().id() : null,
                response.stage(),
                response.utcDate() != null ? Instant.parse(response.utcDate()) : null
        );
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "<null>";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
