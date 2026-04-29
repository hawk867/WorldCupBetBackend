package org.danielesteban.worldcupbetbackend.support;

import org.danielesteban.worldcupbetbackend.persistence.JpaAuditingConfig;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for Spring Data JPA integration tests.
 * <p>
 * Extending this class gives a test:
 * <ul>
 *   <li>A single shared logical database ({@code wcbet_suite_test}) on the
 *       developer-managed PostgreSQL server described by {@link TestDatabase}.
 *       The database is created lazily by {@link SharedTestDatabase} and
 *       reused by every test class in the suite; Spring's context cache can
 *       therefore reuse the same ApplicationContext across classes.</li>
 *   <li>Flyway migrations applied automatically by Spring Boot on context
 *       startup (thanks to {@code spring-boot-starter-flyway}, which
 *       re-introduces Flyway auto-configuration in Spring Boot 4).</li>
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
@Import(JpaAuditingConfig.class)
public abstract class AbstractRepositoryIT {

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        SharedTestDatabase.ensureCreated();
        registry.add("spring.datasource.url",
                () -> TestDatabase.jdbcUrlFor(SharedTestDatabase.NAME));
        registry.add("spring.datasource.username", () -> TestDatabase.USER);
        registry.add("spring.datasource.password", () -> TestDatabase.PASSWORD);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
    }
}
