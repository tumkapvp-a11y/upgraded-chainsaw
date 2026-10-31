package me.x1no4ekk.core.api;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.teleport.TeleportOptions;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link CoreApi} implementation registered in Bukkit's ServicesManager.
 */
public final class CoreApiImpl implements CoreApi {

    private final CorePlugin plugin;
    private volatile GuildTagProvider guildTagProvider;

    public CoreApiImpl(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isInCombat(UUID player) {
        return plugin.combatManager().isInCombat(player);
    }

    @Override
    public int getCombatSecondsLeft(UUID player) {
        return plugin.combatManager().getRemainingSeconds(player);
    }

    @Override
    public boolean isVanished(UUID player) {
        return plugin.vanishManager().isVanished(player);
    }

    @Override
    public CompletableFuture<TeleportResult> teleport(Player player, Location destination, boolean warmup) {
        TeleportOptions options = warmup ? TeleportOptions.PLAYER : new TeleportOptions(false, true, true, true);
        return plugin.teleportService().teleport(player, destination, options);
    }

    @Override
    public void setGuildTagProvider(@Nullable GuildTagProvider provider) {
        this.guildTagProvider = provider;
    }

    @Override
    public @Nullable GuildTagProvider getGuildTagProvider() {
        return guildTagProvider;
    }
}
