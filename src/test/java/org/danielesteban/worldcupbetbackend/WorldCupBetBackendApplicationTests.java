package org.danielesteban.worldcupbetbackend;

import org.danielesteban.worldcupbetbackend.support.SharedTestDatabase;
import org.danielesteban.worldcupbetbackend.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Smoke test that boots the full Spring context against the shared test
 * database. Verifies the application is wired correctly (Flyway +
 * auto-configuration + entity scanning) end-to-end.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
class WorldCupBetBackendApplicationTests {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        SharedTestDatabase.ensureCreated();
        registry.add("spring.datasource.url",
                () -> TestDatabase.jdbcUrlFor(SharedTestDatabase.NAME));
        registry.add("spring.datasource.username", () -> TestDatabase.USER);
        registry.add("spring.datasource.password", () -> TestDatabase.PASSWORD);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
    }

    @Test
    void contextLoads() {
    }
}
