package org.danielesteban.worldcupbetbackend.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the data seeder on application startup if configured.
 * Controlled by the {@code football-data.seed.on-startup} property.
 */
@Component
public class DataSeederRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeederRunner.class);

    private final DataSeeder dataSeeder;
    private final FootballDataSeedProperties seedProperties;

    public DataSeederRunner(DataSeeder dataSeeder, FootballDataSeedProperties seedProperties) {
        this.dataSeeder = dataSeeder;
        this.seedProperties = seedProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedProperties.onStartup()) {
            log.info("Seed on startup enabled. Starting data seed...");
            try {
                DataSeeder.SeedResult result = dataSeeder.seedAll();
                log.info("Startup seed completed: {} created, {} updated, {} skipped",
                        result.created(), result.updated(), result.skipped());
            } catch (Exception e) {
                log.error("Startup seed failed: {}", e.getMessage(), e);
            }
        } else {
            log.debug("Seed on startup disabled. Skipping.");
        }
    }
}
