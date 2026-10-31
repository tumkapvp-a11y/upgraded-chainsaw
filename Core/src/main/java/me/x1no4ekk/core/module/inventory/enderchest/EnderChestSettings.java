package me.x1no4ekk.core.module.inventory.enderchest;

import me.x1no4ekk.core.config.Settings;
import me.x1no4ekk.core.module.inventory.ConfigSupport;
import me.x1no4ekk.core.module.inventory.MenuButton;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable {@code enderchest} section of inventories.yml. Navigation button slots are 0-8 inside the bottom row.
 */
public record EnderChestSettings(
        int storageRows,
        int defaultPages,
        int maxPages,
        boolean replaceVanilla,
        boolean importVanilla,
        String title,
        Settings.SoundSettings openSound,
        Settings.SoundSettings closeSound,
        Material filler,
        MenuButton previous,
        MenuButton next,
        MenuButton info
) {

    /** Storage never exceeds five rows: the sixth is the navigation row. */
    public static final int MAX_STORAGE_ROWS = 5;

    public int storageSize() {
        return storageRows * 9;
    }

    public static EnderChestSettings parse(ConfigurationSection s) {
        int maxPages = ConfigSupport.integer(s, "max-pages", 6, 1, 100);
        ConfigurationSection navigation = ConfigSupport.section(s, "navigation");
        MenuButton previous = ConfigSupport.button(navigation, "previous", 0, 8);
        MenuButton next = ConfigSupport.button(navigation, "next", 0, 8);
        MenuButton info = ConfigSupport.button(navigation, "info", 0, 8);
        if (previous.slot() == next.slot() || previous.slot() == info.slot() || next.slot() == info.slot()) {
            throw new IllegalArgumentException(ConfigSupport.path(navigation, "") + " buttons must use different slots");
        }
        return new EnderChestSettings(
                ConfigSupport.integer(s, "storage-rows", 3, 1, MAX_STORAGE_ROWS),
                ConfigSupport.integer(s, "default-pages", 1, 1, maxPages),
                maxPages,
                ConfigSupport.bool(s, "replace-vanilla", true),
                ConfigSupport.bool(s, "import-vanilla", true),
                ConfigSupport.string(s, "title", "Эндер-сундук"),
                ConfigSupport.sound(s, "open-sound"),
                ConfigSupport.sound(s, "close-sound"),
                ConfigSupport.material(navigation.getString("filler"), ConfigSupport.path(navigation, "filler")),
                previous,
                next,
                info
        );
    }
}
