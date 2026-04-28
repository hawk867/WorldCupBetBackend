package org.danielesteban.worldcupbetbackend.domain.enums;

/**
 * Lifecycle states of a {@code Match}.
 * <p>
 * The values mirror the state machine documented in
 * {@code Docs/07-match-state-machine.puml}. Transition rules are enforced by
 * the service layer (out of scope for the persistence layer); this enum only
 * defines the legal value domain that is stored in the {@code matches.status}
 * column as a {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)}.
 */
public enum MatchStatus {
    /** Created, kickoff in the future, predictions accepted. */
    SCHEDULED,
    /** Kickoff reached, predictions locked, score updates in flight. */
    LIVE,
    /** Full time reached; scoring should be triggered. */
    FINISHED,
    /** Admin corrected a previously finished match; scoring must recalculate. */
    ADJUSTED,
    /** Rescheduled; returns to {@link #SCHEDULED} when a new date is set. */
    POSTPONED,
    /** Temporarily halted; returns to {@link #LIVE} or {@link #FINISHED}. */
    SUSPENDED,
    /** Terminal; match will not be played. */
    CANCELLED;

    /**
     * True when the match contributes to user scoring.
     *
     * @return {@code true} iff the status is {@link #FINISHED} or {@link #ADJUSTED}.
     */
    public boolean isScoreBearing() {
        return this == FINISHED || this == ADJUSTED;
    }

    /**
     * True when predictions for this match must be locked (no create/update).
     *
     * @return {@code false} iff the status is {@link #SCHEDULED} or {@link #POSTPONED}.
     */
    public boolean isPredictionLocked() {
        return this != SCHEDULED && this != POSTPONED;
    }
}
