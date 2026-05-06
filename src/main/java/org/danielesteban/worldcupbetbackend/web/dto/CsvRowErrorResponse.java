package org.danielesteban.worldcupbetbackend.web.dto;

/** Error individual de una fila en la carga CSV. */
public record CsvRowErrorResponse(
    int rowNumber,
    String email,
    String reason
) {}
