package org.danielesteban.worldcupbetbackend.support;

/**
 * Connection parameters for the PostgreSQL instance used by the test suite.
 * <p>
 * The tests connect to a developer-managed PostgreSQL 16 container running
 * on {@code localhost:5433} with shared credentials, rather than spinning up
 * a fresh container per run. This keeps test startup fast and avoids
 * Testcontainers' Docker-socket detection issues on macOS / OrbStack.
 * <p>
 * Each integration test creates its own ephemeral logical database against
 * this server (see {@link TestDatabases#createFreshDatabase()}) so runs never
 * share state with each other or with developer data on the shared server.
 * <p>
 * Parameters can be overridden via system properties or environment variables
 * for CI pipelines:
 * <ul>
 *   <li>{@code test.db.host}  / {@code TEST_DB_HOST}</li>
 *   <li>{@code test.db.port}  / {@code TEST_DB_PORT}</li>
 *   <li>{@code test.db.user}  / {@code TEST_DB_USER}</li>
 *   <li>{@code test.db.password} / {@code TEST_DB_PASSWORD}</li>
 *   <li>{@code test.db.adminDatabase} / {@code TEST_DB_ADMIN_DATABASE} — the
 *       database the test connects to in order to {@code CREATE DATABASE};
 *       defaults to {@code postgres}.</li>
 * </ul>
 */
public final class TestDatabase {

    public static final String HOST =
            resolve("test.db.host", "TEST_DB_HOST", "localhost");
    public static final int PORT = Integer.parseInt(
            resolve("test.db.port", "TEST_DB_PORT", "5433"));
    public static final String USER =
            resolve("test.db.user", "TEST_DB_USER", "danielesteban");
    public static final String PASSWORD =
            resolve("test.db.password", "TEST_DB_PASSWORD", "danielesteban");
    public static final String ADMIN_DATABASE =
            resolve("test.db.adminDatabase", "TEST_DB_ADMIN_DATABASE", "postgres");

    /** JDBC URL pointing at the admin database (used to create ephemeral test DBs). */
    public static String adminJdbcUrl() {
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + ADMIN_DATABASE;
    }

    /** JDBC URL pointing at an arbitrary database on the same server. */
    public static String jdbcUrlFor(String databaseName) {
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + databaseName;
    }

    private TestDatabase() {
        // constants holder
    }

    private static String resolve(String systemProperty, String envVar, String fallback) {
        String fromSys = System.getProperty(systemProperty);
        if (fromSys != null && !fromSys.isBlank()) {
            return fromSys;
        }
        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return fallback;
    }
}
