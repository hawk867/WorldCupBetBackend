package org.danielesteban.worldcupbetbackend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * HTTP client for the football-data.org v4 API.
 * <p>
 * Fetches match data for the configured competition (World Cup 2026).
 * Uses Spring 6+ {@link RestClient} with authentication header and timeouts.
 * <p>
 * This is a simplified implementation for the service-layer spec.
 * The football-data-integration spec will later expand this with rate limiting,
 * retry logic, and more detailed DTOs.
 */
@Component
public class FootballDataClient {

    private final RestClient restClient;
    private final Integer competitionId;

    @Autowired
    public FootballDataClient(
            @Value("${football-data.api.base-url}") String baseUrl,
            @Value("${football-data.api.key}") String apiKey,
            @Value("${football-data.api.competition-id}") Integer competitionId,
            @Value("${football-data.api.connection-timeout}") java.time.Duration connectionTimeout,
            @Value("${football-data.api.read-timeout}") java.time.Duration readTimeout) {

        this.competitionId = competitionId;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectionTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Package-private constructor for unit testing with a pre-built RestClient.
     */
    FootballDataClient(RestClient restClient, Integer competitionId) {
        this.restClient = restClient;
        this.competitionId = competitionId;
    }

    /**
     * Fetches all matches for the configured competition (World Cup 2026).
     *
     * @return list of external match DTOs
     * @throws ExternalApiException if the API returns an error or a network failure occurs
     */
    public List<ExternalMatchDto> fetchMatches() {
        try {
            CompetitionMatchesResponse response = restClient.get()
                    .uri("/v4/competitions/{competitionId}/matches", competitionId)
                    .retrieve()
                    .body(CompetitionMatchesResponse.class);

            if (response == null || response.matches() == null) {
                return Collections.emptyList();
            }

            return response.matches().stream()
                    .map(this::toExternalMatchDto)
                    .toList();
        } catch (RestClientException e) {
            throw new ExternalApiException(
                    "Failed to fetch matches from football-data.org: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches a specific match by its external ID.
     *
     * @param externalId the football-data.org match identifier
     * @return the match DTO, or empty if not found
     * @throws ExternalApiException if the API returns an error or a network failure occurs
     */
    public Optional<ExternalMatchDto> fetchMatch(Integer externalId) {
        try {
            MatchResponse response = restClient.get()
                    .uri("/v4/matches/{externalId}", externalId)
                    .retrieve()
                    .body(MatchResponse.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(toExternalMatchDto(response));
        } catch (RestClientException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw new ExternalApiException(
                    "Failed to fetch match " + externalId + " from football-data.org: " + e.getMessage(), e);
        }
    }

    private ExternalMatchDto toExternalMatchDto(MatchResponse response) {
        Integer homeGoals = null;
        Integer awayGoals = null;
        Integer homePenalties = null;
        Integer awayPenalties = null;

        if (response.score() != null) {
            if (response.score().fullTime() != null) {
                homeGoals = response.score().fullTime().home();
                awayGoals = response.score().fullTime().away();
            }
            if (response.score().penalties() != null) {
                homePenalties = response.score().penalties().home();
                awayPenalties = response.score().penalties().away();
            }
        }

        return new ExternalMatchDto(
                response.id(),
                response.status(),
                homeGoals,
                awayGoals,
                homePenalties,
                awayPenalties,
                null,
                null,
                null,
                null
        );
    }

    private boolean isNotFound(RestClientException e) {
        String message = e.getMessage();
        return message != null && message.contains("404");
    }

    // --- Internal DTOs for JSON deserialization ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompetitionMatchesResponse(List<MatchResponse> matches) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MatchResponse(
            Integer id,
            String status,
            MatchScoreResponse score
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MatchScoreResponse(
            ScoreDetail fullTime,
            ScoreDetail penalties
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ScoreDetail(Integer home, Integer away) {}
}
