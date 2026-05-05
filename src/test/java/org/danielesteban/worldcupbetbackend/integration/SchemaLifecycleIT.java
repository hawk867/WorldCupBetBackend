package org.danielesteban.worldcupbetbackend.integration;

import org.danielesteban.worldcupbetbackend.WorldCupBetBackendApplication;
import org.danielesteban.worldcupbetbackend.support.TestDatabase;
import org.danielesteban.worldcupbetbackend.support.TestDatabases;
import org.danielesteban.worldcupbetbackend.support.TestDatabases.EphemeralDatabase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test covering the schema lifecycle:
 * <ol>
 *   <li>Flyway applies V1 + V2 cleanly on a fresh database and Hibernate
 *       validation succeeds.</li>
 *   <li>The seven tournament stages from V2 are present after startup.</li>
 *   <li>Starting a second time against an already-migrated database is a
 *       no-op.</li>
 *   <li>Schema drift (an unexpected DB change after V1 but before the JPA
 *       context refreshes) aborts startup with a
 *       {@link SchemaManagementException}.</li>
 *   <li>A checksum mismatch between the classpath migration and the
 *       {@code flyway_schema_history} row aborts Flyway's validate phase.</li>
 * </ol>
 * <p>
 * Each test creates its own ephemeral database on the shared developer-
 * managed PostgreSQL server (see {@link TestDatabase}) so scenarios never
 * interfere with each other or with the main {@code wcbet_suite_test}
 * database used by repository tests.
 * <p>
 * Scenarios 4 and 5 need to observe startup failures, so they boot the
 * Spring context programmatically inside the test instead of via
 * {@code @SpringBootTest} (which requires the context to load successfully).
 */
class SchemaLifecycleIT {

    private EphemeralDatabase database;

    @AfterEach
    void dropDatabase() {
        if (database != null) {
            TestDatabases.dropDatabase(database.name());
            database = null;
        }
    }

    // --- Scenario 1 + 2 -----------------------------------------------------

    @Test
    @DisplayName("1+2: Flyway applies V1+V2 on a fresh database; seeded stages are present")
    void flywayAppliesMigrationsAndStagesSeed() throws Exception {
        database = TestDatabases.createFreshDatabase();

        try (ConfigurableApplicationContext ctx = bootContextAgainst(database)) {
            // Context started -> Flyway + Hibernate validation both succeeded.
            // Assert flyway_schema_history has exactly two successful rows.
            List<SchemaHistoryEntry> history = readSchemaHistory(database.dataSource());
            assertThat(history).hasSize(2);
            assertThat(history).allSatisfy(h -> assertThat(h.success()).isTrue());
            assertThat(history).extracting(SchemaHistoryEntry::version)
                    .containsExactly("1", "2");

            // Assert the seven stages are present in the expected order.
            List<String> stages = readStagesOrdered(database.dataSource());
            assertThat(stages).containsExactly(
                    "GROUP_STAGE", "ROUND_OF_32", "ROUND_OF_16",
                    "QUARTER_FINAL", "SEMI_FINAL", "THIRD_PLACE", "FINAL");
        }
    }

    // --- Scenario 3 ---------------------------------------------------------

    @Test
    @DisplayName("3: starting a second time is a no-op (no extra history rows)")
    void secondStartupIsNoOp() throws Exception {
        database = TestDatabases.createFreshDatabase();

        // First start applies V1 + V2.
        bootContextAgainst(database).close();
        int rowsAfterFirst = countSchemaHistory(database.dataSource());

        // Second start should not add anything.
        bootContextAgainst(database).close();
        int rowsAfterSecond = countSchemaHistory(database.dataSource());

        assertThat(rowsAfterFirst).isEqualTo(2);
        assertThat(rowsAfterSecond).isEqualTo(rowsAfterFirst);
    }

    // --- Scenario 4 ---------------------------------------------------------

    @Test
    @DisplayName("4: schema drift after migrations aborts startup with SchemaManagementException")
    void schemaDriftAborts() throws Exception {
        database = TestDatabases.createFreshDatabase();

        // Apply the migrations, then mutate the schema behind Flyway's back
        // so Hibernate's validate phase (ddl-auto=validate) detects drift.
        applyMigrations(database.dataSource());
        execute(database.dataSource(), "ALTER TABLE users DROP COLUMN password_changed");

        assertThatThrownBy(() -> bootContextAgainst(database))
                .rootCause()
                .isInstanceOf(SchemaManagementException.class);
    }

    // --- Scenario 5 ---------------------------------------------------------

    @Test
    @DisplayName("5: checksum mismatch between classpath and history aborts migrate")
    void checksumMismatchAborts(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        database = TestDatabases.createFreshDatabase();

        // First apply the real V1 + V2 so flyway_schema_history records the
        // authoritative checksums.
        applyMigrations(database.dataSource());

        // Now build an alternate migration directory with a V1 whose content
        // differs from the original; pointing Flyway at this location must
        // fail validation because the stored checksum no longer matches.
        Path altMigrations = tempDir.resolve("db").resolve("migration");
        Files.createDirectories(altMigrations);
        Files.writeString(altMigrations.resolve("V1__baseline_schema.sql"), """
                -- intentionally different from the real V1 so the checksum
                -- stored in flyway_schema_history does not match
                CREATE TABLE drifted_table (id BIGINT PRIMARY KEY);
                """);

        Flyway altFlyway = Flyway.configure()
                .dataSource(database.dataSource())
                .locations("filesystem:" + altMigrations.getParent().toAbsolutePath())
                .baselineOnMigrate(false)
                .load();

        assertThatThrownBy(altFlyway::migrate)
                .isInstanceOf(FlywayValidateException.class);
    }

    // --- helpers ------------------------------------------------------------

    /**
     * Boots a full Spring Boot context whose datasource points at the given
     * ephemeral database. Properties are passed as command-line arguments
     * (highest priority in Spring Boot's property resolution) so they
     * override any profile-specific YAML files.
     */
    private static ConfigurableApplicationContext bootContextAgainst(EphemeralDatabase db) {
        String jdbcUrl = TestDatabase.jdbcUrlFor(db.name());
        return SpringApplication.run(WorldCupBetBackendApplication.class,
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + TestDatabase.USER,
                "--spring.datasource.password=" + TestDatabase.PASSWORD,
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.open-in-view=false",
                "--spring.flyway.enabled=true",
                "--spring.flyway.locations=classpath:db/migration",
                "--spring.flyway.baseline-on-migrate=false",
                "--spring.main.web-application-type=none"
        );
    }

    private static void applyMigrations(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    private static void execute(DataSource dataSource, String sql) throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement stmt = c.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static int countSchemaHistory(DataSource dataSource) throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<SchemaHistoryEntry> readSchemaHistory(DataSource dataSource) throws Exception {
        List<SchemaHistoryEntry> entries = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT version, success
                     FROM flyway_schema_history
                     WHERE version IS NOT NULL
                     ORDER BY installed_rank
                     """)) {
            while (rs.next()) {
                entries.add(new SchemaHistoryEntry(rs.getString(1), rs.getBoolean(2)));
            }
        }
        return entries;
    }

    private static List<String> readStagesOrdered(DataSource dataSource) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM stages ORDER BY order_idx ASC")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    private record SchemaHistoryEntry(String version, boolean success) { }
}
