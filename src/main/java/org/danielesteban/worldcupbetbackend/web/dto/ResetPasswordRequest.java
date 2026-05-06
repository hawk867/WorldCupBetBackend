package org.danielesteban.worldcupbetbackend.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/admin/users/{userId}/reset-password */
public record ResetPasswordRequest(
    @NotBlank String newPassword
) {}
