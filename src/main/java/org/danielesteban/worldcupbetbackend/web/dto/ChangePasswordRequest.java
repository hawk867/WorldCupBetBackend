package org.danielesteban.worldcupbetbackend.web.dto;

import jakarta.validation.constraints.NotBlank;

/** PUT /api/auth/password */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank String newPassword
) {}
