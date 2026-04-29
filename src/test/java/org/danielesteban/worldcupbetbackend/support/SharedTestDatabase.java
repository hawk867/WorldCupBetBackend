package org.danielesteban.worldcupbetbackend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * A single shared logical PostgreSQL database used by every repository
 * integration test in the suite.
 * <p>
 * The database is created idempotently the first time a test triggers
 * {@link #ensureCreated()} and dropped once, at JVM shutdown, through a
 * shutdown hook. Tests that extend
 * {@link AbstractRepositoryIT} benefit from {@code @DataJpaTest}'s automatic
 * transaction rollback to keep data isolated between tests without needing a
 * brand-new database per test class.
 * <p>
 * Sharing a single database across the suite matters because Spring's test
 * context cache keeps ApplicationContexts alive across test classes; a
 * per-class database would be dropped while another class's context still
 * holds a Hikari pool pointing at it, leading to "database does not exist"
 * errors on the second class.
 */
public final class SharedTestDatabase {

    /** Stable database name used by every test in this suite. */
    public static final String NAME = "wcbet_suite_test";

    private static volatile boolean created = false;
    private static final Object LOCK = new Object();

    private SharedTestDatabase() {
        // utility
    }

    /**
     * Creates the shared database if it does not already exist. Safe to call
     * from multiple threads; the first caller wins and subsequent calls are
     * no-ops.
     */
    public static void ensureCreated() {
        if (created) {
            return;
        }
        synchronized (LOCK) {
            if (created) {
                return;
            }
            try (Connection admin = DriverManager.getConnection(
                    TestDatabase.adminJdbcUrl(),
                    TestDatabase.USER,
                    TestDatabase.PASSWORD);
                 Statement stmt = admin.createStatement()) {

                if (!databaseExists(stmt, NAME)) {
                    stmt.execute("CREATE DATABASE \"" + NAME + "\"");
                } else {
                    dropAllOwnedObjects();
                }
                created = true;
                Runtime.getRuntime().addShutdownHook(
                        new Thread(SharedTestDatabase::dropQuietly, "wcbet-testdb-cleanup"));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to create shared test database " + NAME + " on "
                                + TestDatabase.adminJdbcUrl(), e);
            }
        }
    }

    /**
     * If a previous run left the shared database behind with tables/data,
     * wipe it clean so Flyway can re-apply the baseline migrations.
     */
    private static void dropAllOwnedObjects() {
        try (Connection c = DriverManager.getConnection(
                TestDatabase.jdbcUrlFor(NAME),
                TestDatabase.USER,
                TestDatabase.PASSWORD);
             Statement stmt = c.createStatement()) {
            stmt.execute("DROP SCHEMA public CASCADE");
            stmt.execute("CREATE SCHEMA public");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to clean shared test database " + NAME, e);
        }
    }

    private static boolean databaseExists(Statement stmt, String name) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM pg_database WHERE datname = '" + name + "'")) {
            return rs.next();
        }
    }

    private static void dropQuietly() {
        try (Connection admin = DriverManager.getConnection(
                TestDatabase.adminJdbcUrl(),
                TestDatabase.USER,
                TestDatabase.PASSWORD);
             Statement stmt = admin.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS \"" + NAME + "\" WITH (FORCE)");
        } catch (Exception ignored) {
            // shutdown-time cleanup is best-effort
        }
    }
}
