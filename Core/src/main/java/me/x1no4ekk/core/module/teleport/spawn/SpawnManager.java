package me.x1no4ekk.core.module.teleport.spawn;

import me.x1no4ekk.core.CorePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Server spawn stored in {@code core_locations} under the name "spawn" and cached as an immutable record.
 * Falls back to the main world's spawn when unset or when its world is not loaded.
 */
public final class SpawnManager {

    private static final String SPAWN_NAME = "spawn";

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS core_locations (
                name VARCHAR(32) NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                updated_at BIGINT NOT NULL,
                PRIMARY KEY (name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""";

    private static final String SELECT = "SELECT world, x, y, z, yaw, pitch FROM core_locations WHERE name = ?";

    private static final String UPSERT = "INSERT INTO core_locations (name, world, x, y, z, yaw, pitch, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), "
            + "yaw = VALUES(yaw), pitch = VALUES(pitch), updated_at = VALUES(updated_at)";

    /**
     * World-name based location, so no {@link World} reference is retained.
     */
    public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {

        static StoredLocation of(Location location) {
            return new StoredLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }

        /**
         * @return the location, or {@code null} if the world is not loaded
         */
        public Location toLocation() {
            World loaded = Bukkit.getWorld(world);
            return loaded == null ? null : new Location(loaded, x, y, z, yaw, pitch);
        }
    }

    private final CorePlugin plugin;
    private volatile StoredLocation spawn;

    public SpawnManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Blocking; only called from the module's enable().
     */
    public void createTableAndLoad() throws SQLException {
        plugin.database().execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_TABLE);
            }
            return null;
        });
        this.spawn = plugin.database().execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
                statement.setString(1, SPAWN_NAME);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new StoredLocation(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                }
            }
        });
        if (spawn != null && Bukkit.getWorld(spawn.world()) == null) {
            plugin.getLogger().warning("Spawn world '" + spawn.world() + "' is not loaded; the main world spawn is used instead");
        }
    }

    /**
     * Configured spawn, or the main world's spawn as a fallback. Main thread only.
     */
    public Location spawnLocation() {
        StoredLocation stored = spawn;
        Location location = stored == null ? null : stored.toLocation();
        return location != null ? location : Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    /**
     * Sets the spawn to the player's position: the cache and the world spawn are updated at once,
     * the row is written asynchronously and the player is told the outcome.
     */
    public void setSpawn(Player player) {
        Location location = player.getLocation();
        StoredLocation stored = StoredLocation.of(location);
        this.spawn = stored;
        location.getWorld().setSpawnLocation(location);

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        plugin.database().update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, SPAWN_NAME);
                statement.setString(2, stored.world());
                statement.setDouble(3, stored.x());
                statement.setDouble(4, stored.y());
                statement.setDouble(5, stored.z());
                statement.setFloat(6, stored.yaw());
                statement.setFloat(7, stored.pitch());
                statement.setLong(8, now);
                statement.executeUpdate();
            }
        }).whenCompleteAsync((ignored, error) -> {
            Player online = Bukkit.getPlayer(uuid);
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save spawn location", error);
                if (online != null) {
                    plugin.messages().send(online, "general.database-error");
                }
                return;
            }
            if (online != null) {
                plugin.messages().send(online, "spawn.set",
                        Placeholder.unparsed("world", stored.world()),
                        Placeholder.unparsed("x", format(stored.x())),
                        Placeholder.unparsed("y", format(stored.y())),
                        Placeholder.unparsed("z", format(stored.z())));
            }
        }, plugin.mainThread());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
