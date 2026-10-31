package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.teleport.TeleportOptions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Teleports to warps through the shared TeleportService and counts successful visits.
 */
final class WarpTeleporter {

    private final CorePlugin plugin;
    private final WarpManager manager;

    WarpTeleporter(CorePlugin plugin, WarpManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    void teleport(Player player, Warp warp) {
        Location destination = warp.toLocation();
        if (destination == null) {
            plugin.messages().send(player, "warp.world-not-loaded", Placeholder.unparsed("world", warp.world()));
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = warp.name();
        boolean own = warp.isOwnedBy(uuid);
        plugin.teleportService().teleport(player, destination, TeleportOptions.PLAYER)
                .thenAcceptAsync(result -> {
                    if (result != TeleportResult.SUCCESS) {
                        return;
                    }
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        plugin.messages().send(online, "warp.teleported", Placeholder.unparsed("warp", name));
                    }
                    // Owners visiting their own warp would inflate the ranking of the players menu.
                    if (!own) {
                        manager.recordVisit(name);
                    }
                }, plugin.mainThread());
    }
}
