package org.danielesteban.worldcupbetbackend.support;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for Spring Data JPA integration tests.
 * <p>
 * Extending this class gives a test:
 * <ul>
 *   <li>A private, ephemeral PostgreSQL 16 database created on the shared
 *       test server (see {@link TestDatabase}) before the test class runs,
 *       and dropped afterward.</li>
 *   <li>Flyway migrations applied automatically by Spring Boot on context
 *       startup.</li>
 *   <li>Hibernate configured with {@code ddl-auto: validate}, so the test
 *       fails fast if an entity mapping drifts from the migration schema.</li>
 *   <li>Automatic transaction rollback between tests via {@code @DataJpaTest}
 *       so fixtures from one test do not leak into another.</li>
 *   <li>{@code open-in-view: false} to mirror production behavior and catch
 *       accidental lazy-loading outside transactions.</li>
 * </ul>
 * <p>
 * The default Spring Boot test datasource replacement is disabled via
 * {@link AutoConfigureTestDatabase.Replace#NONE} so tests run against the real
 * PostgreSQL rather than an embedded database.
 * <p>
 * Note: In Spring Boot 4, {@code @DataJpaTest} lives under
 * {@code org.springframework.boot.data.jpa.test.autoconfigure} and
 * {@code @AutoConfigureTestDatabase} under
 * {@code org.springframework.boot.jdbc.test.autoconfigure}.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
public abstract class AbstractRepositoryIT {

    /**
     * Ephemeral database for the current test class. A single database is
     * shared by every test method in the class (and reset between tests by
     * the {@code @DataJpaTest} rollback), then dropped in {@link #dropDatabase()}.
     */
    private static TestDatabases.EphemeralDatabase database;

    @BeforeAll
    static void createDatabase() {
        database = TestDatabases.createFreshDatabase();
    }

    @AfterAll
    static void dropDatabase() {
        if (database != null) {
            TestDatabases.dropDatabase(database.name());
            database = null;
        }
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> TestDatabase.jdbcUrlFor(database.name()));
        registry.add("spring.datasource.username", () -> TestDatabase.USER);
        registry.add("spring.datasource.password", () -> TestDatabase.PASSWORD);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
    }
}
