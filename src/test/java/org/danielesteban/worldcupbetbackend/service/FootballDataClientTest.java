package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.service.dto.ExternalMatchDto;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FootballDataClientTest {

    private MockRestServiceServer mockServer;
    private FootballDataClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.football-data.org")
                .defaultHeader("X-Auth-Token", "test-key");

        mockServer = MockRestServiceServer.bindTo(builder).build();

        client = new FootballDataClient(builder.build(), 2000);
    }

    @Test
    void fetchMatches_returnsMatchList() {
        String json = """
                {
                  "matches": [
                    {
                      "id": 330299,
                      "status": "SCHEDULED",
                      "score": {
                        "fullTime": { "home": null, "away": null },
                        "penalties": { "home": null, "away": null }
                      }
                    },
                    {
                      "id": 330300,
                      "status": "FINISHED",
                      "score": {
                        "fullTime": { "home": 2, "away": 1 },
                        "penalties": { "home": null, "away": null }
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/2000/matches"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Auth-Token", "test-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ExternalMatchDto> matches = client.fetchMatches();

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).id()).isEqualTo(330299);
        assertThat(matches.get(0).status()).isEqualTo("SCHEDULED");
        assertThat(matches.get(0).homeGoals()).isNull();
        assertThat(matches.get(0).awayGoals()).isNull();

        assertThat(matches.get(1).id()).isEqualTo(330300);
        assertThat(matches.get(1).status()).isEqualTo("FINISHED");
        assertThat(matches.get(1).homeGoals()).isEqualTo(2);
        assertThat(matches.get(1).awayGoals()).isEqualTo(1);

        mockServer.verify();
    }

    @Test
    void fetchMatches_withPenalties_mapsPenaltyFields() {
        String json = """
                {
                  "matches": [
                    {
                      "id": 330301,
                      "status": "FINISHED",
                      "score": {
                        "fullTime": { "home": 1, "away": 1 },
                        "penalties": { "home": 4, "away": 2 }
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/2000/matches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ExternalMatchDto> matches = client.fetchMatches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).homePenalties()).isEqualTo(4);
        assertThat(matches.get(0).awayPenalties()).isEqualTo(2);

        mockServer.verify();
    }

    @Test
    void fetchMatches_serverError_throwsExternalApiException() {
        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/2000/matches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchMatches())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch matches from football-data.org");

        mockServer.verify();
    }

    @Test
    void fetchMatch_returnsMatch() {
        String json = """
                {
                  "id": 330299,
                  "status": "LIVE",
                  "score": {
                    "fullTime": { "home": 1, "away": 0 },
                    "penalties": { "home": null, "away": null }
                  }
                }
                """;

        mockServer.expect(requestTo("https://api.football-data.org/v4/matches/330299"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Auth-Token", "test-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<ExternalMatchDto> result = client.fetchMatch(330299);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(330299);
        assertThat(result.get().status()).isEqualTo("LIVE");
        assertThat(result.get().homeGoals()).isEqualTo(1);
        assertThat(result.get().awayGoals()).isEqualTo(0);

        mockServer.verify();
    }

    @Test
    void fetchMatch_notFound_returnsEmpty() {
        mockServer.expect(requestTo("https://api.football-data.org/v4/matches/999999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<ExternalMatchDto> result = client.fetchMatch(999999);

        assertThat(result).isEmpty();

        mockServer.verify();
    }

    @Test
    void fetchMatch_serverError_throwsExternalApiException() {
        mockServer.expect(requestTo("https://api.football-data.org/v4/matches/330299"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchMatch(330299))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch match 330299");

        mockServer.verify();
    }

    @Test
    void fetchMatches_emptyResponse_returnsEmptyList() {
        String json = """
                {
                  "matches": []
                }
                """;

        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/2000/matches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ExternalMatchDto> matches = client.fetchMatches();

        assertThat(matches).isEmpty();

        mockServer.verify();
    }

    @Test
    void fetchMatches_noScoreField_returnsNullGoals() {
        String json = """
                {
                  "matches": [
                    {
                      "id": 330302,
                      "status": "SCHEDULED",
                      "score": null
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/2000/matches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ExternalMatchDto> matches = client.fetchMatches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).homeGoals()).isNull();
        assertThat(matches.get(0).awayGoals()).isNull();
        assertThat(matches.get(0).homePenalties()).isNull();
        assertThat(matches.get(0).awayPenalties()).isNull();

        mockServer.verify();
    }
}
