package me.x1no4ekk.core.api;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Supplies guild information for Core's chat (implemented by the Guilds plugin).
 * Called from the async chat thread: implementations must be thread-safe and must not block.
 */
public interface GuildTagProvider {

    /**
     * @return formatted guild tag shown in chat, or {@code null} if the player has no guild
     */
    @Nullable Component guildTag(UUID player);

    /**
     * @return plain guild name, or {@code null} if the player has no guild
     */
    @Nullable String guildName(UUID player);
}
