package me.x1no4ekk.core.module.inventory;

import me.x1no4ekk.core.config.Settings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Validating readers for the module's YAML files. Every getter goes through {@link ConfigurationSection#get(String)},
 * so values missing from the server's file fall back to the bundled defaults attached by {@code ConfigFile}.
 * Invalid values throw {@link IllegalArgumentException} with the full path, which keeps the previous config on reload.
 */
public final class ConfigSupport {

    private ConfigSupport() {
    }

    public static String path(ConfigurationSection parent, String key) {
        String base = parent.getCurrentPath();
        return base == null || base.isEmpty() ? key : base + "." + key;
    }

    public static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("Missing section '" + path(parent, key) + "'");
        }
        return section;
    }

    public static String string(ConfigurationSection section, String key, String fallback) {
        String value = section.getString(key);
        return value == null ? fallback : value;
    }

    public static boolean bool(ConfigurationSection section, String key, boolean fallback) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = raw.toString().trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException(path(section, key) + ": expected true or false, got '" + raw + "'");
    }

    public static int integer(ConfigurationSection section, String key, int fallback, int min, int max) {
        return (int) longValue(section, key, fallback, min, max);
    }

    public static long longValue(ConfigurationSection section, String key, long fallback, long min, long max) {
        Object raw = section.get(key);
        long value;
        if (raw == null) {
            value = fallback;
        } else if (raw instanceof Number number) {
            if (number.doubleValue() != Math.rint(number.doubleValue())) {
                throw new IllegalArgumentException(path(section, key) + ": expected a whole number, got '" + raw + "'");
            }
            value = number.longValue();
        } else {
            try {
                value = Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(path(section, key) + ": expected a whole number, got '" + raw + "'");
            }
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(path(section, key) + " must be between " + min + " and " + max + ", got " + value);
        }
        return value;
    }

    public static double decimal(ConfigurationSection section, String key, double fallback, double min, double max) {
        Object raw = section.get(key);
        double value;
        if (raw == null) {
            value = fallback;
        } else if (raw instanceof Number number) {
            value = number.doubleValue();
        } else {
            try {
                value = Double.parseDouble(raw.toString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(path(section, key) + ": expected a number, got '" + raw + "'");
            }
        }
        if (Double.isNaN(value) || value < min || value > max) {
            throw new IllegalArgumentException(path(section, key) + " must be between " + min + " and " + max + ", got " + raw);
        }
        return value;
    }

    public static Material material(@Nullable String raw, String path) {
        Material material = raw == null ? null : Material.matchMaterial(raw.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException(path + ": unknown item material '" + raw + "'");
        }
        return material;
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, @Nullable String raw, String path) {
        if (raw != null) {
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(normalized)) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException(path + ": unknown value '" + raw + "'");
    }

    /**
     * Filler material of {@code parent.<key>} ({@code enabled}, {@code material}), or {@code null} if disabled.
     */
    public static @Nullable Material filler(ConfigurationSection parent, String key) {
        ConfigurationSection filler = section(parent, key);
        if (!bool(filler, "enabled", true)) {
            return null;
        }
        return material(filler.getString("material"), path(filler, "material"));
    }

    /**
     * @param minSlot first allowed slot (inclusive)
     * @param maxSlot last allowed slot (inclusive)
     */
    public static MenuButton button(ConfigurationSection parent, String key, int minSlot, int maxSlot) {
        ConfigurationSection button = section(parent, key);
        int slot = integer(button, "slot", -1, minSlot, maxSlot);
        return new MenuButton(
                slot,
                material(button.getString("material"), path(button, "material")),
                string(button, "name", ""),
                button.getStringList("lore")
        );
    }

    /**
     * Sound section ({@code key}, {@code volume}, {@code pitch}); an empty key disables the sound.
     */
    public static Settings.SoundSettings sound(ConfigurationSection parent, String key) {
        ConfigurationSection sound = parent.getConfigurationSection(key);
        if (sound == null) {
            return new Settings.SoundSettings("", 1F, 1F);
        }
        return new Settings.SoundSettings(
                string(sound, "key", ""),
                (float) decimal(sound, "volume", 1.0, 0.0, 10.0),
                (float) decimal(sound, "pitch", 1.0, 0.5, 2.0)
        );
    }
}
