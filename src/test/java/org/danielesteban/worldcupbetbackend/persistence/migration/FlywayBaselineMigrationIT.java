package org.danielesteban.worldcupbetbackend.persistence.migration;

import org.danielesteban.worldcupbetbackend.support.TestDatabases;
import org.danielesteban.worldcupbetbackend.support.TestDatabases.EphemeralDatabase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that validates the Flyway baseline migrations (V1 + V2)
 * apply cleanly against a fresh PostgreSQL 16 database.
 * <p>
 * Each test creates its own ephemeral database on the shared test server
 * (see {@code TestDatabase}) and drops it afterward, so runs never share
 * state. Uses Flyway and JDBC directly so it does not depend on any JPA
 * entities (those are added in later tasks).
 */
class FlywayBaselineMigrationIT {

    private static final List<String> EXPECTED_TABLES = List.of(
            "users", "teams", "stages", "matches",
            "predictions", "prediction_scores", "user_scores", "audit_log"
    );

    private static final List<String> EXPECTED_STAGES_IN_ORDER = List.of(
            "GROUP_STAGE", "ROUND_OF_32", "ROUND_OF_16",
            "QUARTER_FINAL", "SEMI_FINAL", "THIRD_PLACE", "FINAL"
    );

    private EphemeralDatabase database;

    @AfterEach
    void cleanup() {
        if (database != null) {
            TestDatabases.dropDatabase(database.name());
            database = null;
        }
    }

    @Test
    @DisplayName("V1 + V2 apply cleanly against a fresh PostgreSQL 16 database")
    void baselineMigrationsApplyCleanly() throws Exception {
        database = TestDatabases.createFreshDatabase();
        DataSource dataSource = database.dataSource();

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).as("migration success flag").isTrue();
        assertThat(result.migrationsExecuted)
                .as("number of migrations executed")
                .isEqualTo(2);

        assertThat(listTableNames(dataSource))
                .as("tables created by V1")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

        assertThat(listStageNamesOrdered(dataSource))
                .as("stages seeded by V2 in order")
                .containsExactlyElementsOf(EXPECTED_STAGES_IN_ORDER);
    }

    @Test
    @DisplayName("running migrate again is a no-op (idempotent)")
    void secondMigrateIsNoOp() throws Exception {
        database = TestDatabases.createFreshDatabase();
        Flyway flyway = Flyway.configure()
                .dataSource(database.dataSource())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(second.success).isTrue();
        assertThat(second.migrationsExecuted)
                .as("second migrate should not apply any new migration")
                .isZero();
    }

    // --- helpers -------------------------------------------------------------

    private static List<String> listTableNames(DataSource ds) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public'
                       AND table_type = 'BASE TABLE'
                       AND table_name <> 'flyway_schema_history'
                     """)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    private static List<String> listStageNamesOrdered(DataSource ds) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM stages ORDER BY order_idx ASC")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }
}
