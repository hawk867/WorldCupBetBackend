package org.danielesteban.worldcupbetbackend.web.dto;

/** Equipo embebido en respuesta. */
public record TeamResponse(
    Long id,
    String name,
    String code,
    String flagUrl
) {}
