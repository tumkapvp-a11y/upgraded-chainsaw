package dev.applesub.data;

import dev.applesub.AppleSubPlugin;
import dev.applesub.storage.Database;
import org.bukkit.Material;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {

    private final AppleSubPlugin plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(AppleSubPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public void cache(PlayerData data) {
        cache.put(data.uuid(), data);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    /** Blocking load; call off the main thread. */
    public PlayerData loadNow(UUID uuid, String name) throws SQLException {
        Database database = plugin.database();
        Database.PlayerRow row = database.loadPlayer(uuid);
        PlayerData data;
        if (row == null) {
            data = PlayerData.defaults(uuid, name, plugin.settings());
        } else {
            data = PlayerData.of(row, name);
            if (!name.equals(row.name())) {
                database.saveSettings(data.snapshot());
            }
        }
        for (String materialName : database.loadTrash(uuid)) {
            Material material = Material.matchMaterial(materialName);
            if (material != null && material.isItem()) {
                data.customTrash().add(material);
            }
        }
        return data;
    }

    public CompletableFuture<PlayerData> loadAsync(UUID uuid, String name) {
        return plugin.database().supply(() -> loadNow(uuid, name));
    }

    /** Persists feature toggles and name (not the expiry, which is always written by the service). */
    public void saveSettings(PlayerData data) {
        Database.PlayerRow row = data.snapshot();
        Database database = plugin.database();
        database.run(() -> database.saveSettings(row));
    }

    public void addTrash(PlayerData data, Material material) {
        Database.PlayerRow row = data.snapshot();
        Database database = plugin.database();
        // The settings row must exist so a player with a custom list always shows up in the table.
        database.run(() -> {
            database.saveSettings(row);
            database.addTrash(row.uuid(), material.name());
        });
    }

    public void removeTrash(PlayerData data, Material material) {
        UUID uuid = data.uuid();
        Database database = plugin.database();
        database.run(() -> database.removeTrash(uuid, material.name()));
    }
}
