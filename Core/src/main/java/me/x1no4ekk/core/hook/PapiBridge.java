package me.x1no4ekk.core.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

/**
 * Touches PlaceholderAPI classes; only used when PlaceholderAPI is enabled.
 */
final class PapiBridge {

    private PapiBridge() {
    }

    static String apply(OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
