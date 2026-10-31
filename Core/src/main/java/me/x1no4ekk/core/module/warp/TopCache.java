package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Periodically refreshed tops (balance, kills, deaths). Queries run on the database read pool;
 * commands only read the last snapshot and never touch MySQL.
 */
final class TopCache {

    enum Type {
        BALANCE("balance"), KILLS("kills"), DEATHS("deaths");

        private final String messageSection;

        Type(String messageSection) {
            this.messageSection = messageSection;
        }

        String messageSection() {
            return messageSection;
        }
    }

    record Entry(String name, double value) {
    }

    record Snapshot(List<Entry> entries, long updatedAt) {
    }

    private static final String KILLS_SQL =
            "SELECT u.name, s.kills FROM core_stats s JOIN core_users u ON u.uuid = s.uuid ORDER BY s.kills DESC LIMIT ?";
    private static final String DEATHS_SQL =
            "SELECT u.name, s.deaths FROM core_stats s JOIN core_users u ON u.uuid = s.uuid ORDER BY s.deaths DESC LIMIT ?";

    private final CorePlugin plugin;
    private final Supplier<WarpConfig> config;
    private final Map<Type, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    /** Balance table may not exist until the Economy plugin creates it: warn once per failure streak. */
    private final AtomicBoolean balanceWarned = new AtomicBoolean();
    private BukkitTask task;
    private int scheduledMinutes;

    TopCache(CorePlugin plugin, Supplier<WarpConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    void start() {
        schedule(config.get().tops().refreshMinutes());
    }

    /**
     * Applies a changed refresh interval after /core reload.
     */
    void reschedule() {
        int minutes = config.get().tops().refreshMinutes();
        if (task == null || minutes != scheduledMinutes) {
            stop();
            schedule(minutes);
        }
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void schedule(int minutes) {
        long period = minutes * 60L * 20L;
        this.scheduledMinutes = minutes;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1L, period);
    }

    @Nullable Snapshot get(Type type) {
        return snapshots.get(type);
    }

    private void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return; // previous refresh still running (slow database)
        }
        WarpConfig.Tops settings = config.get().tops();
        CompletableFuture.allOf(
                load(Type.BALANCE, settings.balanceQuery(), settings.size()),
                load(Type.KILLS, KILLS_SQL, settings.size()),
                load(Type.DEATHS, DEATHS_SQL, settings.size())
        ).whenComplete((ignored, error) -> refreshing.set(false));
    }

    private CompletableFuture<Void> load(Type type, String sql, int size) {
        return plugin.database().query(connection -> read(connection, sql, size))
                .handle((entries, error) -> {
                    if (error == null) {
                        snapshots.put(type, new Snapshot(entries, System.currentTimeMillis()));
                        if (type == Type.BALANCE) {
                            balanceWarned.set(false);
                        }
                    } else if (type == Type.BALANCE) {
                        if (balanceWarned.compareAndSet(false, true)) {
                            plugin.getLogger().warning("Balance top is unavailable (is the Economy plugin installed?): "
                                    + rootMessage(error) + ". Keeping the previous list.");
                        }
                    } else {
                        plugin.getLogger().log(Level.WARNING, "Failed to refresh the " + type.messageSection() + " top", error);
                    }
                    return null;
                });
    }

    private static List<Entry> read(Connection connection, String sql, int size) throws SQLException {
        List<Entry> entries = new ArrayList<>(size);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, size);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    BigDecimal value = rs.getBigDecimal(2);
                    if (name != null) {
                        entries.add(new Entry(name, value == null ? 0 : value.doubleValue()));
                    }
                }
            }
        }
        return List.copyOf(entries);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
