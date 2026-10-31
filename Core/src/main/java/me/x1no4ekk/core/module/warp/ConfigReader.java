package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.config.Settings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation helpers shared by {@link WarpConfig} and {@link KillerMoneyConfig}.
 * Every failure is an {@link IllegalArgumentException} carrying the full YAML path.
 */
final class ConfigReader {

    private ConfigReader() {
    }

    static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("Missing section '" + path(parent, key) + "'");
        }
        return section;
    }

    static String path(ConfigurationSection parent, String key) {
        String base = parent.getCurrentPath();
        return base == null || base.isEmpty() ? key : base + "." + key;
    }

    static Material material(String raw, String path) {
        Material material = raw == null ? null : Material.matchMaterial(raw.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException(path + ": unknown item material '" + raw + "'");
        }
        return material;
    }

    static int rows(ConfigurationSection s) {
        return Math.clamp(s.getInt("rows", 6), 1, 6);
    }

    static int slot(ConfigurationSection s, String key, int rows) {
        int slot = s.getInt(key, -1);
        if (slot < 0 || slot >= rows * 9) {
            throw new IllegalArgumentException(path(s, key) + " must be inside the menu (0-" + (rows * 9 - 1) + ")");
        }
        return slot;
    }

    static List<Integer> slots(ConfigurationSection s, String key, int rows) {
        List<Integer> slots = new ArrayList<>();
        for (Integer slot : s.getIntegerList(key)) {
            if (slot < 0 || slot >= rows * 9) {
                throw new IllegalArgumentException(path(s, key) + " contains slot " + slot + " outside the menu");
            }
            if (!slots.contains(slot)) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            throw new IllegalArgumentException(path(s, key) + " must not be empty");
        }
        return List.copyOf(slots);
    }

    /**
     * Accepts either {@code key: 'minecraft:sound.id'} or a section with key/volume/pitch.
     */
    static Settings.SoundSettings sound(ConfigurationSection parent, String key) {
        if (parent.isConfigurationSection(key)) {
            ConfigurationSection s = section(parent, key);
            return new Settings.SoundSettings(s.getString("key", ""), (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0));
        }
        return new Settings.SoundSettings(parent.getString(key, ""), 1F, 1F);
    }
}
