package org.danielesteban.worldcupbetbackend.service.dto;

import java.util.List;

public record CsvUploadResult(int createdCount, List<CsvRowError> errors) {}
