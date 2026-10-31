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
 * HikariCP pool plus dedicated executors for blocking JDBC work (never the server main thread).
 * Every connection is borrowed and returned with try-with-resources.
 */
public final class DatabaseManager {

    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final Logger logger;
    private final Settings.DatabaseSettings settings;
    private HikariDataSource dataSource;
    /** Parallel executor for reads. */
    private ExecutorService readExecutor;
    /**
     * Single writer thread: writes run strictly in submission order, so e.g. /sethome followed by /delhome,
     * or an autosave followed by a quit-save, can never be reordered and overwrite newer data.
     */
    private ExecutorService writeExecutor;

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

        this.readExecutor = Executors.newFixedThreadPool(Math.max(1, settings.maximumPoolSize() - 1), new DatabaseThreadFactory("Core-DB-Read-"));
        this.writeExecutor = Executors.newSingleThreadExecutor(new DatabaseThreadFactory("Core-DB-Write-"));
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
            statement.executeUpdate("DELETE FROM core_cooldowns WHERE expires_at < " + System.currentTimeMillis());
        }
    }

    /**
     * Asynchronous read on the parallel read executor.
     */
    public <T> CompletableFuture<T> query(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(work);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, readExecutor);
    }

    /**
     * Asynchronous write on the serial writer thread.
     */
    public CompletableFuture<Void> update(SqlConsumer work) {
        return CompletableFuture.runAsync(() -> {
            try {
                execute(connection -> {
                    work.accept(connection);
                    return null;
                });
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, writeExecutor);
    }

    /**
     * Asynchronous transactional write on the serial writer thread.
     */
    public CompletableFuture<Void> updateInTransaction(SqlConsumer work) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeInTransaction(connection -> {
                    work.accept(connection);
                    return null;
                });
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, writeExecutor);
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

    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed()
                && writeExecutor != null && !writeExecutor.isShutdown();
    }

    /**
     * Drains queued writes (final saves included), then closes the pool. Blocking; only called from onDisable.
     */
    public void close() {
        shutdownExecutor(readExecutor, "read");
        shutdownExecutor(writeExecutor, "write");
        closePool();
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Database " + name + " tasks did not finish in 15 seconds; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
        private final String prefix;

        private DatabaseThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
