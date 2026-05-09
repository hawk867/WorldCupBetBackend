package org.danielesteban.worldcupbetbackend.websocket.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MatchUpdateMessagePropertyTest {

    private final ObjectMapper mapper;

    MatchUpdateMessagePropertyTest() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // Feature: websocket-realtime, Property 2: Mapeo correcto de Match a MatchUpdateMessage
    @Property(tries = 100)
    void fromMatchPreservesAllFields(@ForAll("arbitraryMatches") Match match) {
        // **Validates: Requirements 3.3, 9.3**
        MatchUpdateMessage message = MatchUpdateMessage.from(match);

        assertThat(message.matchId()).isEqualTo(match.getId());
        assertThat(message.status()).isEqualTo(match.getStatus());
        assertThat(message.homeGoals()).isEqualTo(match.getHomeGoals());
        assertThat(message.awayGoals()).isEqualTo(match.getAwayGoals());
        assertThat(message.homePenalties()).isEqualTo(match.getHomePenalties());
        assertThat(message.awayPenalties()).isEqualTo(match.getAwayPenalties());
        assertThat(message.wentToPenalties()).isEqualTo(match.isWentToPenalties());
        assertThat(message.updatedAt()).isEqualTo(match.getUpdatedAt());
    }

    // Feature: websocket-realtime, Property 3: Serialización round-trip de Instant en formato ISO-8601
    @Property(tries = 100)
    void instantSerializesAsIso8601AndRoundTrips(@ForAll("instants") Instant instant) throws Exception {
        // **Validates: Requirements 3.4, 8.3**
        MatchUpdateMessage message = new MatchUpdateMessage(
            1L, MatchStatus.LIVE, 1, 0, null, null, false, instant
        );

        String json = mapper.writeValueAsString(message);

        // Verify ISO-8601 format
        assertThat(json).contains("\"updatedAt\":");
        String updatedAtValue = extractJsonField(json, "updatedAt");
        assertThat(updatedAtValue).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");

        // Round-trip: deserialize back
        MatchUpdateMessage deserialized = mapper.readValue(json, MatchUpdateMessage.class);
        assertThat(deserialized.updatedAt()).isEqualTo(instant);
    }

    @Provide
    Arbitrary<Match> arbitraryMatches() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 10_000);
        Arbitrary<MatchStatus> statuses = Arbitraries.of(MatchStatus.values());
        Arbitrary<Integer> goals = Arbitraries.integers().between(0, 15);
        Arbitrary<Integer> nullableGoals = Arbitraries.integers().between(0, 15).injectNull(0.3);
        Arbitrary<Boolean> booleans = Arbitraries.of(true, false);
        Arbitrary<Instant> instants = instants();

        return Combinators.combine(ids, statuses, goals, goals, nullableGoals, nullableGoals, booleans, instants)
            .as((id, status, hg, ag, hp, ap, penalties, updatedAt) -> {
                Match m = new Match();
                m.setId(id);
                m.setStatus(status);
                m.setHomeGoals(hg);
                m.setAwayGoals(ag);
                m.setHomePenalties(hp);
                m.setAwayPenalties(ap);
                m.setWentToPenalties(penalties);
                m.setUpdatedAt(updatedAt);
                return m;
            });
    }

    @Provide
    Arbitrary<Instant> instants() {
        return Arbitraries.longs()
            .between(0, 4_102_444_800L)
            .map(Instant::ofEpochSecond);
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key) + key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
