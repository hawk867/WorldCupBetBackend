package org.danielesteban.worldcupbetbackend.service.dto;

import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;

public record JwtClaims(Long userId, String email, UserRole role) {}
