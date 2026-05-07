package org.danielesteban.worldcupbetbackend.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.integration.dto.*;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.dto.ExternalTeamDto;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FootballDataClientPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Feature: football-data-integration, Property 3: Mapeo de equipos preserva todos los campos
    // Validates: Requirements 2.2, 14.1, 14.2, 14.3
    @Property(tries = 100)
    void teamMappingPreservesAllFields(
            @ForAll("teamIds") Integer id,
            @ForAll("teamNames") String name,
            @ForAll("tlaCodes") String tla,
            @ForAll("crestUrls") String crest) throws JsonProcessingException {

        // Build a mock RestClient that returns a JSON response with the team
        CompetitionTeamsResponse apiResponse = new CompetitionTeamsResponse(
                List.of(new TeamResponse(id, name, tla, crest))
        );
        String jsonBody = objectMapper.writeValueAsString(apiResponse);

        FootballDataClient client = createClientWithResponse(jsonBody);
        List<ExternalTeamDto> result = client.fetchTeams();

        assertThat(result).hasSize(1);
        ExternalTeamDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo(name);
        assertThat(dto.tla()).isEqualTo(tla);
        assertThat(dto.crest()).isEqualTo(crest);
    }

    // Feature: football-data-integration, Property 4: Mapeo de partidos preserva todos los campos
    // Validates: Requirements 3.2, 13.1, 13.2, 13.3, 13.4
    @Property(tries = 100)
    void matchMappingPreservesAllFields(
            @ForAll("matchIds") Integer matchId,
            @ForAll("validStatuses") String status,
            @ForAll("scores") Integer homeGoals,
            @ForAll("scores") Integer awayGoals,
            @ForAll("nullableScores") Integer homePenalties,
            @ForAll("nullableScores") Integer awayPenalties,
            @ForAll("teamIds") Integer homeTeamId,
            @ForAll("teamIds") Integer awayTeamId,
            @ForAll("stageNames") String stage,
            @ForAll("utcDates") String utcDate) throws JsonProcessingException {

        MatchResponse matchResponse = new MatchResponse(
                matchId, utcDate, status, stage,
                new MatchScoreResponse(null, null,
                        new ScoreDetail(homeGoals, awayGoals),
                        null,
                        new ScoreDetail(homePenalties, awayPenalties)),
                new MatchTeamResponse(homeTeamId, "Home", "HOM", null),
                new MatchTeamResponse(awayTeamId, "Away", "AWY", null)
        );

        // Test the package-private toExternalMatchDto method directly
        FootballDataClient client = createClientWithResponse("{}");
        ExternalMatchDto dto = client.toExternalMatchDto(matchResponse);

        assertThat(dto.id()).isEqualTo(matchId);
        assertThat(dto.status()).isEqualTo(status);
        assertThat(dto.homeGoals()).isEqualTo(homeGoals);
        assertThat(dto.awayGoals()).isEqualTo(awayGoals);
        assertThat(dto.homePenalties()).isEqualTo(homePenalties);
        assertThat(dto.awayPenalties()).isEqualTo(awayPenalties);
        assertThat(dto.homeTeamExternalId()).isEqualTo(homeTeamId);
        assertThat(dto.awayTeamExternalId()).isEqualTo(awayTeamId);
        assertThat(dto.stageName()).isEqualTo(stage);
        assertThat(dto.kickoffAt()).isEqualTo(Instant.parse(utcDate));
    }

    // Feature: football-data-integration, Property 5: Partidos con equipos TBD son filtrados
    // Validates: Requirements 3.3
    @Property(tries = 100)
    void matchesWithTbdTeamsAreFiltered(
            @ForAll("matchListsWithTbd") List<MatchResponse> matches) throws JsonProcessingException {

        CompetitionMatchesResponse apiResponse = new CompetitionMatchesResponse(matches);
        String jsonBody = objectMapper.writeValueAsString(apiResponse);

        FootballDataClient client = createClientWithResponse(jsonBody);
        List<ExternalMatchDto> result = client.fetchMatches();

        // Count expected: matches where both homeTeam.id and awayTeam.id are non-null
        long expectedCount = matches.stream()
                .filter(m -> m.homeTeam() != null && m.homeTeam().id() != null
                        && m.awayTeam() != null && m.awayTeam().id() != null)
                .count();

        assertThat(result).hasSize((int) expectedCount);
        // All returned matches must have both team IDs defined
        for (ExternalMatchDto dto : result) {
            assertThat(dto.homeTeamExternalId()).isNotNull();
            assertThat(dto.awayTeamExternalId()).isNotNull();
        }
    }

    // Feature: football-data-integration, Property 6: Errores HTTP producen ExternalApiException
    // Validates: Requirements 2.3, 3.4, 4.4
    @Property(tries = 100)
    void httpErrorsProduceExternalApiException(@ForAll("httpErrorCodes") int statusCode) {
        FootballDataClient client = createClientWithErrorStatus(statusCode);

        assertThatThrownBy(client::fetchTeams)
                .isInstanceOf(ExternalApiException.class);

        assertThatThrownBy(client::fetchMatches)
                .isInstanceOf(ExternalApiException.class);
    }

    // Feature: football-data-integration, Property 13: Respuestas inválidas producen ExternalApiException
    // Validates: Requirements 15.1, 15.2
    @Property(tries = 100)
    void malformedJsonResponsesProduceExternalApiException(
            @ForAll("malformedJsonBodies") String malformedJson) {

        FootballDataClient client = createClientWithResponse(malformedJson);

        assertThatThrownBy(client::fetchTeams)
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("deserialize");
    }

    // --- Helper methods ---

    @SuppressWarnings("unchecked")
    private FootballDataClient createClientWithResponse(String responseBody) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);

        FootballDataProperties properties = mock(FootballDataProperties.class);
        when(properties.competitionId()).thenReturn(2000);

        return new FootballDataClient(restClient, properties);
    }

    @SuppressWarnings("unchecked")
    private FootballDataClient createClientWithErrorStatus(int statusCode) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        // Simulate the onStatus handler throwing ExternalApiException
        when(responseSpec.onStatus(any(), any())).thenAnswer(invocation -> {
            java.util.function.Predicate<HttpStatusCode> predicate = invocation.getArgument(0);
            if (predicate.test(HttpStatusCode.valueOf(statusCode))) {
                throw new ExternalApiException(statusCode, "HTTP error: " + statusCode);
            }
            return responseSpec;
        });

        FootballDataProperties properties = mock(FootballDataProperties.class);
        when(properties.competitionId()).thenReturn(2000);

        return new FootballDataClient(restClient, properties);
    }

    // --- Providers ---

    @Provide
    Arbitrary<Integer> teamIds() {
        return Arbitraries.integers().between(1, 10000);
    }

    @Provide
    Arbitrary<String> teamNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> tlaCodes() {
        return Arbitraries.strings().alpha().ofLength(3);
    }

    @Provide
    Arbitrary<String> crestUrls() {
        return Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                .map(s -> "https://crests.example.com/" + s + ".svg");
    }

    @Provide
    Arbitrary<Integer> matchIds() {
        return Arbitraries.integers().between(1, 100000);
    }

    @Provide
    Arbitrary<String> validStatuses() {
        return Arbitraries.of("SCHEDULED", "TIMED", "IN_PLAY", "PAUSED",
                "FINISHED", "POSTPONED", "SUSPENDED", "CANCELLED");
    }

    @Provide
    Arbitrary<Integer> scores() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<Integer> nullableScores() {
        return Arbitraries.integers().between(0, 10).injectNull(0.5);
    }

    @Provide
    Arbitrary<String> stageNames() {
        return Arbitraries.of("GROUP_STAGE", "LAST_16", "QUARTER_FINALS",
                "SEMI_FINALS", "THIRD_PLACE", "FINAL");
    }

    @Provide
    Arbitrary<String> utcDates() {
        return Arbitraries.longs().between(
                Instant.parse("2025-06-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2026-12-31T23:59:59Z").getEpochSecond()
        ).map(epoch -> Instant.ofEpochSecond(epoch).toString());
    }

    @Provide
    Arbitrary<Integer> httpErrorCodes() {
        return Arbitraries.integers().between(400, 599);
    }

    @Provide
    Arbitrary<String> malformedJsonBodies() {
        return Arbitraries.of(
                "{invalid json",
                "not json at all",
                "{\"teams\": [{ \"id\": \"not_a_number\" }]}",
                "<html>error</html>",
                "{ broken: true }",
                "",
                "[[[",
                "{{malformed",
                "{\"teams\": \"not_an_array\"}"
        );
    }

    @Provide
    Arbitrary<List<MatchResponse>> matchListsWithTbd() {
        Arbitrary<MatchResponse> validMatch = Combinators.combine(
                Arbitraries.integers().between(1, 100000),
                utcDates(),
                validStatuses(),
                stageNames()
        ).as((id, date, status, stage) -> new MatchResponse(
                id, date, status, stage,
                new MatchScoreResponse(null, null, new ScoreDetail(0, 0), null, null),
                new MatchTeamResponse(100, "Home", "HOM", null),
                new MatchTeamResponse(200, "Away", "AWY", null)
        ));

        Arbitrary<MatchResponse> tbdMatch = Combinators.combine(
                Arbitraries.integers().between(1, 100000),
                utcDates(),
                validStatuses(),
                stageNames(),
                Arbitraries.of(true, false) // true = null homeTeam id, false = null awayTeam id
        ).as((id, date, status, stage, nullHome) -> new MatchResponse(
                id, date, status, stage,
                new MatchScoreResponse(null, null, new ScoreDetail(null, null), null, null),
                nullHome ? new MatchTeamResponse(null, "TBD", null, null)
                        : new MatchTeamResponse(100, "Home", "HOM", null),
                nullHome ? new MatchTeamResponse(200, "Away", "AWY", null)
                        : new MatchTeamResponse(null, "TBD", null, null)
        ));

        return Combinators.combine(
                validMatch.list().ofMinSize(0).ofMaxSize(5),
                tbdMatch.list().ofMinSize(0).ofMaxSize(5)
        ).as((valid, tbd) -> {
            List<MatchResponse> all = new ArrayList<>(valid);
            all.addAll(tbd);
            return all;
        });
    }
}
