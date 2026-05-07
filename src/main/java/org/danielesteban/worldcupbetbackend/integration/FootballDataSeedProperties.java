package org.danielesteban.worldcupbetbackend.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "football-data.seed")
public record FootballDataSeedProperties(
        boolean onStartup
) {
    public FootballDataSeedProperties {
        // default false is handled by primitive boolean
    }
}
