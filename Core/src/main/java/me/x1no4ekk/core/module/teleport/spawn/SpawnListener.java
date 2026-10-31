package me.x1no4ekk.core.module.teleport.spawn;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.module.teleport.TeleportConfig;
import me.x1no4ekk.core.teleport.TeleportOptions;
import me.x1no4ekk.core.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sends players to the spawn on first join / every join and on respawn without a bed or respawn anchor.
 */
public final class SpawnListener implements Listener {

    /** Join teleports: instant, no combat check, and no /back point at the logout position. */
    private static final TeleportOptions JOIN = new TeleportOptions(false, false, false, false);

    private final CorePlugin plugin;
    private final SpawnManager spawns;
    private final Supplier<TeleportConfig> config;

    public SpawnListener(CorePlugin plugin, SpawnManager spawns, Supplier<TeleportConfig> config) {
        this.plugin = plugin;
        this.spawns = spawns;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        TeleportConfig.Spawn settings = config.get().spawn();
        Player player = event.getPlayer();
        boolean teleport = settings.teleportOnJoin();
        if (!teleport && settings.teleportNewPlayers()) {
            User user = plugin.userManager().getUser(player);
            teleport = user != null && user.isNewPlayer();
        }
        if (!teleport) {
            return;
        }
        UUID uuid = player.getUniqueId();
        // One tick later: the client must finish joining or the position may be overwritten.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                plugin.teleportService().teleport(online, spawns.spawnLocation(), JOIN);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        TeleportConfig.Spawn settings = config.get().spawn();
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.PLUGIN) {
            return;
        }
        boolean always = settings.alwaysRespawnAtSpawn();
        if (!always && (!settings.respawnAtSpawn() || event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }
        event.setRespawnLocation(spawns.spawnLocation());
    }
}
