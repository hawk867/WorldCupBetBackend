package org.danielesteban.worldcupbetbackend.service.dto;

public record CsvRowError(int rowNumber, String email, String reason) {}
