package org.danielesteban.worldcupbetbackend.web.dto;

/** Etapa embebida en respuesta. */
public record StageResponse(
    Long id,
    String name,
    Integer orderIdx
) {}
