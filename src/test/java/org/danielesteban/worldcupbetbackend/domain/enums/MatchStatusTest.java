package org.danielesteban.worldcupbetbackend.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive unit tests for {@link MatchStatus} helper methods.
 * <p>
 * The tests enumerate every value of the enum to ensure that adding or
 * removing a value is caught here rather than by downstream surprises.
 */
class MatchStatusTest {

    private static final Set<MatchStatus> SCORE_BEARING =
            EnumSet.of(MatchStatus.FINISHED, MatchStatus.ADJUSTED);

    private static final Set<MatchStatus> PREDICTION_OPEN =
            EnumSet.of(MatchStatus.SCHEDULED, MatchStatus.POSTPONED);

    @ParameterizedTest
    @EnumSource(MatchStatus.class)
    @DisplayName("isScoreBearing() is true iff status is FINISHED or ADJUSTED")
    void isScoreBearing_matchesDesignContract(MatchStatus status) {
        assertThat(status.isScoreBearing())
                .isEqualTo(SCORE_BEARING.contains(status));
    }

    @ParameterizedTest
    @EnumSource(MatchStatus.class)
    @DisplayName("isPredictionLocked() is false iff status is SCHEDULED or POSTPONED")
    void isPredictionLocked_matchesDesignContract(MatchStatus status) {
        assertThat(status.isPredictionLocked())
                .isEqualTo(!PREDICTION_OPEN.contains(status));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("MatchStatus declares exactly the seven values specified by the design")
    void enumDeclaresExactlyTheSevenDesignValues() {
        assertThat(MatchStatus.values())
                .containsExactly(
                        MatchStatus.SCHEDULED,
                        MatchStatus.LIVE,
                        MatchStatus.FINISHED,
                        MatchStatus.ADJUSTED,
                        MatchStatus.POSTPONED,
                        MatchStatus.SUSPENDED,
                        MatchStatus.CANCELLED);
    }
}
