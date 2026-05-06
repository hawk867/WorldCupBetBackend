package org.danielesteban.worldcupbetbackend.web.dto;

import java.time.Instant;

/** Respuesta de error estandarizada. */
public record ErrorResponse(
    int status,
    String error,
    String message,
    Instant timestamp
) {}
