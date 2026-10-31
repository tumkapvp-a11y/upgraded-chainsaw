package me.x1no4ekk.core.module.teleport;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Command sender captured for an asynchronous reply without holding a {@link Player} reference.
 * Non-player senders (console, RCON, command blocks) are answered through the console.
 *
 * @param playerId the player's UUID, or {@code null} for non-player senders
 */
public record SenderRef(@Nullable UUID playerId) {

    public static SenderRef of(CommandSender sender) {
        return new SenderRef(sender instanceof Player player ? player.getUniqueId() : null);
    }

    /**
     * @return the online player, the console for non-player senders, or {@code null} if the player left
     */
    public @Nullable CommandSender resolve() {
        return playerId == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(playerId);
    }
}
