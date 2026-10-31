package me.x1no4ekk.core.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API of Core for other plugins (Guilds, Economy). Obtain with {@link #get()} after Core is enabled.
 */
public interface CoreApi {

    /**
     * @return the API, or {@code null} if Core is not enabled
     */
    static @Nullable CoreApi get() {
        return Bukkit.getServicesManager().load(CoreApi.class);
    }

    /**
     * Thread-safe.
     */
    boolean isInCombat(UUID player);

    /**
     * @return seconds until the combat tag expires, 0 if not in combat. Thread-safe.
     */
    int getCombatSecondsLeft(UUID player);

    /**
     * Thread-safe.
     */
    boolean isVanished(UUID player);

    /**
     * Safe asynchronous teleport with Core's warmup, combat check, /back recording and messages.
     * Must be called on the main thread; the future completes on the main thread.
     *
     * @param warmup {@code true} to apply the configured warmup (players with the bypass permission skip it)
     */
    CompletableFuture<TeleportResult> teleport(Player player, Location destination, boolean warmup);

    /**
     * Registers the provider of guild tags for chat; {@code null} removes it.
     */
    void setGuildTagProvider(@Nullable GuildTagProvider provider);

    @Nullable GuildTagProvider getGuildTagProvider();
}
