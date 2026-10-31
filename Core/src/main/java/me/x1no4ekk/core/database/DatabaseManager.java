package me.x1no4ekk.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.x1no4ekk.core.config.Settings;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HikariCP pool plus a dedicated executor for blocking JDBC work.
 * Every connection is borrowed and returned with try-with-resources.
 */
public final class DatabaseManager {

    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final Logger logger;
    private final Settings.DatabaseSettings settings;
    private HikariDataSource dataSource;
    private ExecutorService executor;

    public DatabaseManager(Logger logger, Settings.DatabaseSettings settings) {
        this.logger = logger;
        this.settings = settings;
    }

    /**
     * Opens the pool and validates a connection. Blocking; only called from onEnable.
     *
     * @throws SQLException             if MySQL is unreachable or credentials are wrong
     * @throws IllegalStateException    if the JDBC driver is missing
     */
    public void connect() throws SQLException {
        try {
            Class.forName(settings.driverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver not found: " + settings.driverClass(), e);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("Core-MySQL");
        config.setDriverClassName(settings.driverClass());
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.maximumPoolSize());
        config.setMinimumIdle(settings.minimumIdle());
        config.setConnectionTimeout(settings.connectionTimeout());
        config.setIdleTimeout(settings.idleTimeout());
        config.setMaxLifetime(settings.maxLifetime());
        config.setKeepaliveTime(settings.keepaliveTime());
        config.setLeakDetectionThreshold(settings.leakDetectionThreshold());
        // Fail fast: the pool constructor throws if the first connection cannot be established.
        config.setInitializationFailTimeout(1);
        settings.properties().forEach(config::addDataSourceProperty);

        try {
            this.dataSource = new HikariDataSource(config);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new SQLException("Unable to open MySQL pool: " + cause.getMessage(), cause);
        }

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new SQLException("MySQL connection did not pass validation");
            }
        } catch (SQLException e) {
            closePool();
            throw e;
        }

        this.executor = Executors.newFixedThreadPool(settings.maximumPoolSize(), new DatabaseThreadFactory());
    }

    /**
     * Creates all tables. Blocking; only called from onEnable.
     */
    public void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : Schema.STATEMENTS) {
                statement.execute(ddl);
            }
        }
    }

    /**
     * Runs work on the database executor.
     */
    public <T> CompletableFuture<T> supplyAsync(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(work);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> runAsync(SqlConsumer work) {
        return supplyAsync(connection -> {
            work.accept(connection);
            return null;
        });
    }

    /**
     * Runs work on the calling thread. Only for threads that may block:
     * AsyncPlayerPreLoginEvent, the database executor itself, and plugin shutdown.
     */
    public <T> T execute(SqlFunction<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return work.apply(connection);
        }
    }

    /**
     * Runs work inside a single transaction on the calling thread.
     */
    public <T> T executeInTransaction(SqlFunction<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public <T> CompletableFuture<T> supplyInTransactionAsync(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeInTransaction(work);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed() && executor != null && !executor.isShutdown();
    }

    /**
     * Waits for queued work, then closes the pool. Blocking; only called from onDisable.
     */
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    logger.warning("Database tasks did not finish in 15 seconds; forcing shutdown.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        closePool();
    }

    private void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to roll back transaction", e);
        }
    }

    private static final class DatabaseThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Core-Database-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
