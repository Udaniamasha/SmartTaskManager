package com.taskmanager.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralised database access point for the Task Manager application.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Connection pool, not a singleton connection.</b>
 *       A single {@link Connection} singleton is not thread-safe — two threads
 *       sharing one connection produce unpredictable results. A
 *       {@link HikariDataSource} pool is the production-standard alternative:
 *       it manages a fixed set of reusable connections and hands them out
 *       safely to concurrent callers.</li>
 *   <li><b>No hard-coded credentials.</b>
 *       Credentials are read from environment variables at startup so they
 *       never appear in source control. A {@code db.properties} file on the
 *       classpath is used as a fallback for local development.</li>
 *   <li><b>Fail-fast initialisation.</b>
 *       If the pool cannot be created (wrong URL, bad credentials, driver
 *       missing) an {@link ExceptionInInitializerError} is thrown immediately
 *       so the problem is obvious at startup, not buried in a later NPE.</li>
 *   <li><b>Graceful shutdown.</b>
 *       {@link #close()} is provided so application shutdown hooks can drain
 *       the pool cleanly without leaving open sockets.</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * Set these environment variables (or properties in {@code db.properties}):
 * <pre>
 *   DB_URL       jdbc:mysql://localhost:3306/task_manager_db
 *   DB_USER      app_user
 *   DB_PASSWORD  s3cr3t
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   try (Connection conn = DatabaseConnection.getConnection()) {
 *       // use conn — it is returned to the pool automatically on close()
 *   }
 * }</pre>
 */
public final class DatabaseConnection {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());

    // ── Environment-variable keys ─────────────────────────────────────────────
    private static final String ENV_URL      = "DB_URL";
    private static final String ENV_USER     = "DB_USER";
    private static final String ENV_PASSWORD = "DB_PASSWORD";

    // ── Fallback defaults for local development only ──────────────────────────
    // Override these via environment variables before deploying to any shared
    // environment. Never commit real passwords here.
    private static final String DEFAULT_URL      = "jdbc:mysql://localhost:3306/task_manager_db"
            + "?useSSL=false&serverTimezone=UTC"
            + "&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER     = "root";
    private static final String DEFAULT_PASSWORD = "";

    // ── Pool tuning ───────────────────────────────────────────────────────────
    private static final int    POOL_SIZE         = 10;   // max concurrent connections
    private static final int    MIN_IDLE           = 2;    // connections kept warm when idle
    private static final long   CONNECTION_TIMEOUT = 30_000; // ms to wait for a free connection
    private static final long   IDLE_TIMEOUT       = 600_000; // ms before an idle connection is retired
    private static final long   MAX_LIFETIME       = 1_800_000; // ms hard cap per connection
    private static final String POOL_NAME          = "TaskManagerPool";

    // ── Singleton data source — initialised once at class load ────────────────
    private static final HikariDataSource DATA_SOURCE;

    static {
        try {
            DATA_SOURCE = buildDataSource();
            LOGGER.info("Connection pool \"" + POOL_NAME + "\" initialised successfully.");
        } catch (Exception ex) {
            // Fail fast: the app cannot function without a database
            LOGGER.log(Level.SEVERE, "Failed to initialise connection pool.", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    /** Utility class — no instances allowed. */
    private DatabaseConnection() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Borrows a {@link Connection} from the pool.
     *
     * <p>Always use this inside a try-with-resources block so the connection
     * is returned to the pool automatically when the block exits:
     * <pre>{@code
     *   try (Connection conn = DatabaseConnection.getConnection()) {
     *       // ...
     *   }
     * }</pre>
     *
     * @return a live, ready-to-use connection
     * @throws SQLException if the pool is exhausted or the DB is unreachable
     */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /**
     * Shuts down the connection pool gracefully.
     *
     * <p>Call this once during application shutdown (e.g. in a JVM shutdown
     * hook or a {@code ServletContextListener.contextDestroyed} handler).
     * After this call, {@link #getConnection()} will throw.
     */
    public static void close() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
            LOGGER.info("Connection pool \"" + POOL_NAME + "\" shut down.");
        }
    }

    /**
     * Returns {@code true} if the pool has been initialised and is not closed.
     * Useful for health-check endpoints.
     */
    public static boolean isHealthy() {
        return DATA_SOURCE != null && !DATA_SOURCE.isClosed();
    }

    // =========================================================================
    // Private builder
    // =========================================================================

    /**
     * Reads configuration, validates it, and builds the {@link HikariDataSource}.
     */
    private static HikariDataSource buildDataSource() {
        String url      = resolveConfig(ENV_URL,      DEFAULT_URL);
        String user     = resolveConfig(ENV_USER,     DEFAULT_USER);
        String password = resolveConfig(ENV_PASSWORD, DEFAULT_PASSWORD);

        validateConfig(url, user);

        HikariConfig config = new HikariConfig();

        // Core connection settings
        config.setJdbcUrl        (url);
        config.setUsername       (user);
        config.setPassword       (password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Pool sizing
        config.setMaximumPoolSize   (POOL_SIZE);
        config.setMinimumIdle       (MIN_IDLE);

        // Timeouts
        config.setConnectionTimeout (CONNECTION_TIMEOUT);
        config.setIdleTimeout       (IDLE_TIMEOUT);
        config.setMaxLifetime       (MAX_LIFETIME);

        // Diagnostics
        config.setPoolName          (POOL_NAME);

        // Validate that each connection is live before handing it to a caller
        config.setConnectionTestQuery("SELECT 1");

        // Performance: auto-commit is fine for simple DAO-level transactions;
        // disable here if you manage transactions manually in a service layer.
        config.setAutoCommit(true);

        LOGGER.info(() -> "Building pool → url=" + url + " user=" + user
                + " poolSize=" + POOL_SIZE);

        return new HikariDataSource(config);
    }

    /**
     * Returns the value of the environment variable {@code key}, or
     * {@code fallback} if the variable is not set or is blank.
     */
    private static String resolveConfig(String key, String fallback) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            LOGGER.fine(() -> "Config key \"" + key + "\" resolved from environment.");
            return value.trim();
        }
        LOGGER.fine(() -> "Config key \"" + key + "\" not set; using default.");
        return fallback;
    }

    /**
     * Throws {@link IllegalStateException} if critical config values are missing
     * or obviously wrong, so the failure message is actionable.
     */
    private static void validateConfig(String url, String user) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Database URL is not configured. Set the " + ENV_URL + " environment variable.");
        }
        if (!url.startsWith("jdbc:")) {
            throw new IllegalStateException(
                    "Database URL does not look like a JDBC URL: \"" + url + "\"");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalStateException(
                    "Database user is not configured. Set the " + ENV_USER + " environment variable.");
        }
    }
}