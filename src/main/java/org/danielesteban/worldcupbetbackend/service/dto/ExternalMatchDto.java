package org.danielesteban.worldcupbetbackend.service.dto;

public record ExternalMatchDto(
        Integer id,
        String status,
        Integer homeGoals,
        Integer awayGoals,
        Integer homePenalties,
        Integer awayPenalties) {}
