package dev.applesub.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.applesub.util.DurationUtil;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite / MySQL storage. All writes go through one thread so updates for a player
 * are applied in the order they were made.
 */
public final class Database {

    public enum Type { SQLITE, MYSQL }

    public record PlayerRow(UUID uuid, String name, long expiresAt,
                            boolean nightVision, boolean antiFire, boolean antiTrash, boolean armorNotify) {
    }

    public record NamedPlayer(UUID uuid, String name) {
    }

    @FunctionalInterface
    public interface SqlTask {
        void run() throws SQLException;
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    private final Logger logger;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private Type type;
    private String players;
    private String trash;

    public Database(Logger logger) {
        this.logger = logger;
    }

    public void connect(ConfigurationSection config, File dataFolder) throws SQLException {
        String rawType = config.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        type = rawType.equals("mysql") || rawType.equals("mariadb") ? Type.MYSQL : Type.SQLITE;
        String prefix = config.getString("table-prefix", "applesub_").replaceAll("[^A-Za-z0-9_]", "");
        players = prefix + "players";
        trash = prefix + "trash";

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("AppleSub-" + type.name().toLowerCase(Locale.ROOT));
        hikari.setConnectionTimeout(10_000);
        if (type == Type.SQLITE) {
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new SQLException("Не удалось создать папку " + dataFolder);
            }
            File file = new File(dataFolder, config.getString("sqlite.file", "database.db"));
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
        } else {
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl(String.format(Locale.ROOT,
                    "jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true",
                    config.getString("mysql.host", "localhost"),
                    config.getInt("mysql.port", 3306),
                    config.getString("mysql.database", "minecraft"),
                    config.getBoolean("mysql.use-ssl", false)));
            hikari.setUsername(config.getString("mysql.username", "root"));
            hikari.setPassword(config.getString("mysql.password", ""));
            hikari.setMaximumPoolSize(Math.max(2, config.getInt("mysql.pool-size", 5)));
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "64");
            hikari.addDataSourceProperty("useServerPrepStmts", "true");
        }
        dataSource = new HikariDataSource(hikari);
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AppleSub-Database");
            thread.setDaemon(true);
            return thread;
        });
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (type == Type.MYSQL) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + players + " ("
                        + "uuid CHAR(36) NOT NULL,"
                        + "name VARCHAR(32) NOT NULL,"
                        + "expires_at BIGINT NOT NULL DEFAULT 0,"
                        + "night_vision BOOLEAN NOT NULL DEFAULT 0,"
                        + "anti_fire BOOLEAN NOT NULL DEFAULT 0,"
                        + "anti_trash BOOLEAN NOT NULL DEFAULT 0,"
                        + "armor_notify BOOLEAN NOT NULL DEFAULT 1,"
                        + "PRIMARY KEY (uuid),"
                        + "INDEX " + players + "_name (name)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + trash + " ("
                        + "uuid CHAR(36) NOT NULL,"
                        + "material VARCHAR(64) NOT NULL,"
                        + "PRIMARY KEY (uuid, material)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            } else {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + players + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(32) NOT NULL,"
                        + "expires_at BIGINT NOT NULL DEFAULT 0,"
                        + "night_vision BOOLEAN NOT NULL DEFAULT 0,"
                        + "anti_fire BOOLEAN NOT NULL DEFAULT 0,"
                        + "anti_trash BOOLEAN NOT NULL DEFAULT 0,"
                        + "armor_notify BOOLEAN NOT NULL DEFAULT 1)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + players + "_name ON " + players + " (name)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + trash + " ("
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "material VARCHAR(64) NOT NULL,"
                        + "PRIMARY KEY (uuid, material))");
            }
        }
    }

    // ---- queries (blocking) ----

    public PlayerRow loadPlayer(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT name, expires_at, night_vision, anti_fire, "
                     + "anti_trash, armor_notify FROM " + players + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PlayerRow(uuid, rs.getString(1), rs.getLong(2), rs.getBoolean(3),
                        rs.getBoolean(4), rs.getBoolean(5), rs.getBoolean(6));
            }
        }
    }

    public List<String> loadTrash(UUID uuid) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT material FROM " + trash + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }

    public NamedPlayer findByName(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid, name FROM " + players + " WHERE LOWER(name) = LOWER(?) LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new NamedPlayer(UUID.fromString(rs.getString(1)), rs.getString(2)) : null;
            }
        }
    }

    /** Inserts the row or updates name and feature toggles; the expiry of an existing row is left untouched. */
    public void saveSettings(PlayerRow row) throws SQLException {
        String sql = "INSERT INTO " + players
                + " (uuid, name, expires_at, night_vision, anti_fire, anti_trash, armor_notify) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + (type == Type.MYSQL
                ? "ON DUPLICATE KEY UPDATE name = VALUES(name), night_vision = VALUES(night_vision), "
                + "anti_fire = VALUES(anti_fire), anti_trash = VALUES(anti_trash), armor_notify = VALUES(armor_notify)"
                : "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, night_vision = excluded.night_vision, "
                + "anti_fire = excluded.anti_fire, anti_trash = excluded.anti_trash, armor_notify = excluded.armor_notify");
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bindRow(ps, row, row.expiresAt());
            ps.executeUpdate();
        }
    }

    /**
     * Adds {@code duration} to the stored subscription (or starts a new one) in a single transaction.
     *
     * @param defaults values for a row that does not exist yet
     * @return the new expiry
     */
    public long extendSubscription(UUID uuid, String name, long duration, PlayerRow defaults) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long current = null;
                String select = "SELECT expires_at FROM " + players + " WHERE uuid = ?"
                        + (type == Type.MYSQL ? " FOR UPDATE" : "");
                try (PreparedStatement ps = connection.prepareStatement(select)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            current = rs.getLong(1);
                        }
                    }
                }
                long now = System.currentTimeMillis();
                long updated = DurationUtil.extend(current == null ? 0L : current, duration, now);
                if (current != null) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE " + players + " SET expires_at = ?, name = ? WHERE uuid = ?")) {
                        ps.setLong(1, updated);
                        ps.setString(2, name);
                        ps.setString(3, uuid.toString());
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + players
                            + " (uuid, name, expires_at, night_vision, anti_fire, anti_trash, armor_notify) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        bindRow(ps, new PlayerRow(uuid, name, updated, defaults.nightVision(), defaults.antiFire(),
                                defaults.antiTrash(), defaults.armorNotify()), updated);
                        ps.executeUpdate();
                    }
                }
                connection.commit();
                return updated;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void setExpiry(UUID uuid, long expiresAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE " + players + " SET expires_at = ? WHERE uuid = ?")) {
            ps.setLong(1, expiresAt);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    /** Clears an expired subscription unless it was extended in the meantime. */
    public void clearExpired(UUID uuid, long expiredAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + players + " SET expires_at = 0 WHERE uuid = ? AND expires_at = ?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, expiredAt);
            ps.executeUpdate();
        }
    }

    public void addTrash(UUID uuid, String material) throws SQLException {
        String sql = (type == Type.MYSQL ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                + trash + " (uuid, material) VALUES (?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, material);
            ps.executeUpdate();
        }
    }

    public void removeTrash(UUID uuid, String material) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM " + trash + " WHERE uuid = ? AND material = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, material);
            ps.executeUpdate();
        }
    }

    private static void bindRow(PreparedStatement ps, PlayerRow row, long expiresAt) throws SQLException {
        ps.setString(1, row.uuid().toString());
        ps.setString(2, row.name());
        ps.setLong(3, expiresAt);
        ps.setBoolean(4, row.nightVision());
        ps.setBoolean(5, row.antiFire());
        ps.setBoolean(6, row.antiTrash());
        ps.setBoolean(7, row.armorNotify());
    }

    // ---- async ----

    public CompletableFuture<Void> run(SqlTask task) {
        return supply(() -> {
            task.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> supply(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Ошибка базы данных", e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    public Type type() {
        return type;
    }

    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warning("Не все запросы к базе данных успели выполниться");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
