package org.danielesteban.worldcupbetbackend.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/login */
public record LoginRequest(
    @NotBlank String email,
    @NotBlank String password
) {}
