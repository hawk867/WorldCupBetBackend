package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class MatchStateMachine {

    private static final Map<MatchStatus, Set<MatchStatus>> TRANSITIONS = new EnumMap<>(MatchStatus.class);

    static {
        TRANSITIONS.put(MatchStatus.SCHEDULED, EnumSet.of(MatchStatus.LIVE, MatchStatus.POSTPONED, MatchStatus.CANCELLED));
        TRANSITIONS.put(MatchStatus.POSTPONED, EnumSet.of(MatchStatus.SCHEDULED, MatchStatus.CANCELLED));
        TRANSITIONS.put(MatchStatus.LIVE, EnumSet.of(MatchStatus.FINISHED, MatchStatus.SUSPENDED));
        TRANSITIONS.put(MatchStatus.SUSPENDED, EnumSet.of(MatchStatus.LIVE, MatchStatus.FINISHED));
        TRANSITIONS.put(MatchStatus.FINISHED, EnumSet.of(MatchStatus.ADJUSTED));
        TRANSITIONS.put(MatchStatus.ADJUSTED, EnumSet.of(MatchStatus.ADJUSTED));
        TRANSITIONS.put(MatchStatus.CANCELLED, EnumSet.noneOf(MatchStatus.class));
    }

    public static boolean isValidTransition(MatchStatus from, MatchStatus to) {
        Set<MatchStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
