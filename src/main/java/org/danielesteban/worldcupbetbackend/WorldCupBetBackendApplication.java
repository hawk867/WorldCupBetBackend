package org.danielesteban.worldcupbetbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorldCupBetBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorldCupBetBackendApplication.class, args);
    }

}
