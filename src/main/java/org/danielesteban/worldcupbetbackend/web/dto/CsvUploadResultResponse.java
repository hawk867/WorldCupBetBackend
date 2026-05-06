package org.danielesteban.worldcupbetbackend.web.dto;

import java.util.List;

/** Resultado de carga CSV. */
public record CsvUploadResultResponse(
    int createdCount,
    List<CsvRowErrorResponse> errors
) {}
