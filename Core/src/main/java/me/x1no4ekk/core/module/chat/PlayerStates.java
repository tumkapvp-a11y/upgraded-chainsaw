package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.hook.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable per-player snapshots sampled on the main thread, so the async chat thread never touches
 * player positions or Vault (which is main-thread only).
 */
final class PlayerStates {

    record Snapshot(UUID worldId, String worldName, double x, double y, double z,
                    @Nullable String vaultPrefix, @Nullable String vaultSuffix, @Nullable String vaultGroup) {

        boolean isNear(Snapshot other, double radiusSquared) {
            if (!worldId.equals(other.worldId)) {
                return false;
            }
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz <= radiusSquared;
        }

        Snapshot at(World world, double newX, double newY, double newZ) {
            return new Snapshot(world.getUID(), world.getName(), newX, newY, newZ, vaultPrefix, vaultSuffix, vaultGroup);
        }
    }

    private final VaultHook vault;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    PlayerStates(VaultHook vault) {
        this.vault = vault;
    }

    /**
     * Thread-safe.
     */
    @Nullable Snapshot get(UUID uuid) {
        return snapshots.get(uuid);
    }

    /**
     * Main thread only.
     */
    Snapshot refresh(Player player) {
        World world = player.getWorld();
        boolean chat = vault.hasChat();
        Snapshot snapshot = new Snapshot(world.getUID(), world.getName(), player.getX(), player.getY(), player.getZ(),
                chat ? vault.prefix(player) : null,
                chat ? vault.suffix(player) : null,
                chat ? vault.primaryGroup(player) : null);
        snapshots.put(player.getUniqueId(), snapshot);
        return snapshot;
    }

    /**
     * Main thread only. Also drops entries of players that are no longer online.
     */
    void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
        snapshots.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    /**
     * Updates only the position (teleports), keeping the sampled Vault data.
     */
    void move(UUID uuid, Location to) {
        World world = to.getWorld();
        if (world != null) {
            snapshots.computeIfPresent(uuid, (key, snapshot) -> snapshot.at(world, to.getX(), to.getY(), to.getZ()));
        }
    }

    void remove(UUID uuid) {
        snapshots.remove(uuid);
    }

    void clear() {
        snapshots.clear();
    }
}
