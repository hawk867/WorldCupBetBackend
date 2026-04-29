package org.danielesteban.worldcupbetbackend.support;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Locale;

/**
 * Utilities for creating and disposing of ephemeral logical PostgreSQL
 * databases on the shared test server described by {@link TestDatabase}.
 * <p>
 * The helpers here let an integration test obtain a clean database named
 * after a random suffix, apply migrations against it, and drop it when done.
 * This keeps tests independent from each other without needing per-run
 * container churn.
 */
public final class TestDatabases {

    private static final String DRIVER_CLASS = "org.postgresql.Driver";

    private TestDatabases() {
        // utility class
    }

    /**
     * Creates a fresh, empty database on the shared server and returns a
     * DataSource pointing at it.
     *
     * @return an object bundling the new database name and a DataSource
     */
    public static EphemeralDatabase createFreshDatabase() {
        String name = "wcbet_test_" + Long.toHexString(System.nanoTime())
                .toLowerCase(Locale.ROOT);
        executeAdmin("CREATE DATABASE \"" + name + "\"");
        return new EphemeralDatabase(name, dataSourceFor(name));
    }

    /**
     * Drops an ephemeral database previously created with
     * {@link #createFreshDatabase()}. Best-effort: swallows errors.
     */
    public static void dropDatabase(String name) {
        try {
            executeAdmin("DROP DATABASE IF EXISTS \"" + name + "\" WITH (FORCE)");
        } catch (RuntimeException ignored) {
            // cleanup is best-effort; the next CI run will reuse the server
        }
    }

    /**
     * Builds a Spring {@link DriverManagerDataSource} pointing at the given
     * database on the shared test server.
     */
    public static DataSource dataSourceFor(String databaseName) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(DRIVER_CLASS);
        ds.setUrl(TestDatabase.jdbcUrlFor(databaseName));
        ds.setUsername(TestDatabase.USER);
        ds.setPassword(TestDatabase.PASSWORD);
        return ds;
    }

    private static void executeAdmin(String sql) {
        try (Connection c = DriverManager.getConnection(
                TestDatabase.adminJdbcUrl(), TestDatabase.USER, TestDatabase.PASSWORD);
             Statement stmt = c.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Admin SQL failed against " + TestDatabase.adminJdbcUrl() + ": " + sql, e);
        }
    }

    /** An ephemeral database created for a single test; remember to drop it. */
    public record EphemeralDatabase(String name, DataSource dataSource) {
    }
}
