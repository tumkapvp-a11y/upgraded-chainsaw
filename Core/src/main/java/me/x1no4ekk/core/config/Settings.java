package me.x1no4ekk.core.config;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable, validated view of settings.yml. Invalid values throw {@link IllegalArgumentException}
 * with a readable path so startup can hard-fail and /core reload can keep the previous settings.
 */
public record Settings(
        DatabaseSettings database,
        UserSettings users,
        HomeSettings homes,
        HornSettings horn,
        HomeGuiSettings homeGui
) {

    public static Settings load(YamlConfiguration config) {
        return new Settings(
                DatabaseSettings.load(section(config, "database")),
                UserSettings.load(section(config, "users")),
                HomeSettings.load(section(config, "homes")),
                HornSettings.load(section(config, "teleport-horn")),
                HomeGuiSettings.load(section(config, "home-gui"))
        );
    }

    public record DatabaseSettings(
            String host,
            int port,
            String name,
            String username,
            String password,
            String driverClass,
            int maximumPoolSize,
            int minimumIdle,
            long connectionTimeout,
            long idleTimeout,
            long maxLifetime,
            long keepaliveTime,
            long leakDetectionThreshold,
            Map<String, String> properties
    ) {
        static DatabaseSettings load(ConfigurationSection s) {
            ConfigurationSection pool = section(s, "pool");
            Map<String, String> properties = new LinkedHashMap<>();
            ConfigurationSection props = s.getConfigurationSection("properties");
            if (props != null) {
                for (String key : props.getKeys(false)) {
                    properties.put(key, String.valueOf(props.get(key)));
                }
            }
            String host = requireText(s, "host");
            String name = requireText(s, "name");
            String username = requireText(s, "username");
            int port = s.getInt("port", 3306);
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("database.port must be between 1 and 65535");
            }
            int maxPool = Math.max(1, pool.getInt("maximum-pool-size", 10));
            return new DatabaseSettings(
                    host,
                    port,
                    name,
                    username,
                    s.getString("password", ""),
                    s.getString("driver-class", "com.mysql.cj.jdbc.Driver"),
                    maxPool,
                    Math.clamp(pool.getInt("minimum-idle", 2), 0, maxPool),
                    pool.getLong("connection-timeout", 5000L),
                    pool.getLong("idle-timeout", 600000L),
                    pool.getLong("max-lifetime", 1800000L),
                    pool.getLong("keepalive-time", 0L),
                    pool.getLong("leak-detection-threshold", 0L),
                    Map.copyOf(properties)
            );
        }

        public String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + name;
        }
    }

    public record UserSettings(int autosaveIntervalMinutes, int loadTimeoutSeconds) {
        static UserSettings load(ConfigurationSection s) {
            return new UserSettings(
                    Math.max(1, s.getInt("autosave-interval-minutes", 5)),
                    Math.max(1, s.getInt("load-timeout-seconds", 10))
            );
        }
    }

    public record HomeSettings(
            int defaultLimit,
            Map<String, Integer> limits,
            Pattern namePattern,
            Set<String> disabledWorlds
    ) {
        static HomeSettings load(ConfigurationSection s) {
            Map<String, Integer> limits = new LinkedHashMap<>();
            ConfigurationSection limitSection = s.getConfigurationSection("limits");
            if (limitSection != null) {
                for (String group : limitSection.getKeys(false)) {
                    limits.put(group.toLowerCase(Locale.ROOT), Math.max(0, limitSection.getInt(group)));
                }
            }
            Pattern pattern;
            String rawPattern = s.getString("name-pattern", "^[a-zA-Z0-9_-]{1,16}$");
            try {
                pattern = Pattern.compile(rawPattern);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("homes.name-pattern is not a valid regular expression: " + e.getMessage());
            }
            Set<String> disabled = new HashSet<>();
            for (String world : s.getStringList("disabled-worlds")) {
                disabled.add(world.toLowerCase(Locale.ROOT));
            }
            return new HomeSettings(Math.max(0, s.getInt("default-limit", 1)), Map.copyOf(limits), pattern, Set.copyOf(disabled));
        }
    }

    public record HornSettings(
            String name,
            List<String> lore,
            boolean glint,
            String instrument,
            int cooldownSeconds,
            String soundKey,
            float soundVolume,
            float soundPitch,
            boolean recipeEnabled,
            boolean discoverOnJoin,
            List<String> shape,
            Map<Character, Material> ingredients
    ) {
        static HornSettings load(ConfigurationSection s) {
            ConfigurationSection sound = section(s, "sound");
            ConfigurationSection recipe = section(s, "recipe");

            List<String> shape = recipe.getStringList("shape");
            if (shape.isEmpty() || shape.size() > 3) {
                throw new IllegalArgumentException("teleport-horn.recipe.shape must contain 1-3 rows");
            }
            int width = shape.getFirst().length();
            for (String row : shape) {
                if (row.isEmpty() || row.length() > 3 || row.length() != width) {
                    throw new IllegalArgumentException("teleport-horn.recipe.shape rows must have equal length of 1-3 characters");
                }
            }

            Map<Character, Material> ingredients = new LinkedHashMap<>();
            ConfigurationSection ingredientSection = section(recipe, "ingredients");
            for (String key : ingredientSection.getKeys(false)) {
                if (key.length() != 1 || key.charAt(0) == ' ') {
                    throw new IllegalArgumentException("teleport-horn.recipe.ingredients key '" + key + "' must be a single non-space character");
                }
                ingredients.put(key.charAt(0), parseMaterial(ingredientSection.getString(key), "teleport-horn.recipe.ingredients." + key, false));
            }
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !ingredients.containsKey(c)) {
                        throw new IllegalArgumentException("teleport-horn.recipe.shape uses '" + c + "' which has no ingredient");
                    }
                }
            }

            return new HornSettings(
                    s.getString("name", "<gold>Рог телепортации"),
                    List.copyOf(s.getStringList("lore")),
                    s.getBoolean("glint", true),
                    s.getString("instrument", "minecraft:ponder_goat_horn"),
                    Math.max(0, s.getInt("cooldown-seconds", 10)),
                    sound.getString("key", "minecraft:item.goat_horn.sound.0"),
                    (float) sound.getDouble("volume", 1.0),
                    (float) sound.getDouble("pitch", 1.0),
                    recipe.getBoolean("enabled", true),
                    recipe.getBoolean("discover-on-join", true),
                    List.copyOf(shape),
                    Map.copyOf(ingredients)
            );
        }
    }

    public record ButtonSettings(int slot, Material material, String name, List<String> lore) {
        static ButtonSettings load(ConfigurationSection s, String path, int rows) {
            int slot = s.getInt("slot", -1);
            if (slot < 0 || slot >= rows * 9) {
                throw new IllegalArgumentException(path + ".slot must be inside the menu (0-" + (rows * 9 - 1) + ")");
            }
            return new ButtonSettings(
                    slot,
                    parseMaterial(s.getString("material"), path + ".material", true),
                    s.getString("name", ""),
                    List.copyOf(s.getStringList("lore"))
            );
        }
    }

    public record HomeGuiSettings(
            String title,
            String titleOther,
            int rows,
            List<Integer> contentSlots,
            boolean fillerEnabled,
            Material fillerMaterial,
            Map<World.Environment, Material> homeMaterials,
            String homeName,
            List<String> homeLore,
            ButtonSettings emptyItem,
            ButtonSettings previousPage,
            ButtonSettings nextPage,
            ButtonSettings close
    ) {
        static HomeGuiSettings load(ConfigurationSection s) {
            int rows = Math.clamp(s.getInt("rows", 6), 1, 6);
            List<Integer> slots = new ArrayList<>();
            for (Integer slot : s.getIntegerList("content-slots")) {
                if (slot < 0 || slot >= rows * 9) {
                    throw new IllegalArgumentException("home-gui.content-slots contains slot " + slot + " outside the menu");
                }
                if (!slots.contains(slot)) {
                    slots.add(slot);
                }
            }
            if (slots.isEmpty()) {
                throw new IllegalArgumentException("home-gui.content-slots must not be empty");
            }

            ConfigurationSection filler = section(s, "filler");
            ConfigurationSection homeItem = section(s, "home-item");
            ConfigurationSection materials = section(homeItem, "materials");
            Map<World.Environment, Material> homeMaterials = new EnumMap<>(World.Environment.class);
            for (World.Environment environment : World.Environment.values()) {
                String raw = materials.getString(environment.name(), "COMPASS");
                homeMaterials.put(environment, parseMaterial(raw, "home-gui.home-item.materials." + environment.name(), true));
            }

            return new HomeGuiSettings(
                    s.getString("title", "Точки дома"),
                    s.getString("title-other", "Дома игрока <player>"),
                    rows,
                    List.copyOf(slots),
                    filler.getBoolean("enabled", true),
                    parseMaterial(filler.getString("material", "GRAY_STAINED_GLASS_PANE"), "home-gui.filler.material", true),
                    Map.copyOf(homeMaterials),
                    homeItem.getString("name", "<gold><home>"),
                    List.copyOf(homeItem.getStringList("lore")),
                    ButtonSettings.load(section(s, "empty-item"), "home-gui.empty-item", rows),
                    ButtonSettings.load(section(s, "previous-page"), "home-gui.previous-page", rows),
                    ButtonSettings.load(section(s, "next-page"), "home-gui.next-page", rows),
                    ButtonSettings.load(section(s, "close"), "home-gui.close", rows)
            );
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Missing section '" + qualified(parent, path) + "'");
        }
        return section;
    }

    private static String requireText(ConfigurationSection s, String key) {
        String value = s.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + qualified(s, key) + "' must not be empty");
        }
        return value;
    }

    private static String qualified(ConfigurationSection parent, String path) {
        String base = parent.getCurrentPath();
        return base == null || base.isEmpty() ? path : base + "." + path;
    }

    private static Material parseMaterial(String raw, String path, boolean requireItem) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        if (material == null || material.isAir() || (requireItem && !material.isItem())) {
            throw new IllegalArgumentException(path + ": unknown item material '" + raw + "'");
        }
        return material;
    }
}
