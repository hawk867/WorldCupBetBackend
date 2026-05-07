package org.danielesteban.worldcupbetbackend.integration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "football-data.api")
public record FootballDataProperties(
        @NotBlank String baseUrl,
        @NotBlank String key,
        @NotNull Integer competitionId,
        @NotNull Duration connectionTimeout,
        @NotNull Duration readTimeout,
        @Min(1) int rateLimit,
        @Min(1) int maxRetries
) {
    public FootballDataProperties {
        if (rateLimit == 0) rateLimit = 10;
        if (maxRetries == 0) maxRetries = 3;
    }
}
