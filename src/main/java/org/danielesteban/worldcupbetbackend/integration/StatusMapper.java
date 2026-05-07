package org.danielesteban.worldcupbetbackend.integration;

import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StatusMapper {

    private static final Map<String, MatchStatus> MAPPING = Map.ofEntries(
            Map.entry("SCHEDULED", MatchStatus.SCHEDULED),
            Map.entry("TIMED", MatchStatus.SCHEDULED),
            Map.entry("IN_PLAY", MatchStatus.LIVE),
            Map.entry("PAUSED", MatchStatus.LIVE),
            Map.entry("FINISHED", MatchStatus.FINISHED),
            Map.entry("POSTPONED", MatchStatus.POSTPONED),
            Map.entry("SUSPENDED", MatchStatus.SUSPENDED),
            Map.entry("CANCELLED", MatchStatus.CANCELLED)
    );

    /**
     * Translates an external football-data.org match status to the internal MatchStatus enum.
     *
     * @param externalStatus the status string from the external API
     * @return the corresponding internal MatchStatus
     * @throws IllegalArgumentException if the external status is not recognized
     */
    public MatchStatus map(String externalStatus) {
        MatchStatus result = MAPPING.get(externalStatus);
        if (result == null) {
            throw new IllegalArgumentException("Unknown external match status: " + externalStatus);
        }
        return result;
    }
}
