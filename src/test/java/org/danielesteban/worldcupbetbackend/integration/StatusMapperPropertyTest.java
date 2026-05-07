package org.danielesteban.worldcupbetbackend.integration;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusMapperPropertyTest {

    private final StatusMapper statusMapper = new StatusMapper();

    private static final Set<String> VALID_STATUSES = Set.of(
            "SCHEDULED", "TIMED", "IN_PLAY", "PAUSED",
            "FINISHED", "POSTPONED", "SUSPENDED", "CANCELLED"
    );

    private static final Map<String, MatchStatus> EXPECTED_MAPPING = Map.ofEntries(
            Map.entry("SCHEDULED", MatchStatus.SCHEDULED),
            Map.entry("TIMED", MatchStatus.SCHEDULED),
            Map.entry("IN_PLAY", MatchStatus.LIVE),
            Map.entry("PAUSED", MatchStatus.LIVE),
            Map.entry("FINISHED", MatchStatus.FINISHED),
            Map.entry("POSTPONED", MatchStatus.POSTPONED),
            Map.entry("SUSPENDED", MatchStatus.SUSPENDED),
            Map.entry("CANCELLED", MatchStatus.CANCELLED)
    );

    // Feature: football-data-integration, Property 1: Mapeo de estados externos es total y correcto
    // Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.8
    @Property(tries = 100)
    void validExternalStatusMapsToCorrectMatchStatus(@ForAll("validExternalStatuses") String externalStatus) {
        MatchStatus result = statusMapper.map(externalStatus);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(EXPECTED_MAPPING.get(externalStatus));
        assertThat(result).isInstanceOf(MatchStatus.class);
    }

    // Feature: football-data-integration, Property 2: Estados externos no reconocidos producen error
    // Validates: Requirements 5.7
    @Property(tries = 100)
    void unrecognizedExternalStatusThrowsIllegalArgumentException(
            @ForAll("invalidExternalStatuses") String invalidStatus) {

        assertThatThrownBy(() -> statusMapper.map(invalidStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(invalidStatus);
    }

    @Provide
    Arbitrary<String> validExternalStatuses() {
        return Arbitraries.of("SCHEDULED", "TIMED", "IN_PLAY", "PAUSED",
                "FINISHED", "POSTPONED", "SUSPENDED", "CANCELLED");
    }

    @Provide
    Arbitrary<String> invalidExternalStatuses() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !VALID_STATUSES.contains(s));
    }
}
