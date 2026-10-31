package me.x1no4ekk.core.module.warp;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.x1no4ekk.core.config.Settings;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Immutable, validated view of killermoney.yml.
 */
record KillerMoneyConfig(
        boolean enabled,
        boolean requirePermission,
        boolean ignoreCreative,
        boolean ignoreSpawnerMobs,
        PlayerReward player,
        Map<EntityType, MobReward> mobs
) {

    /**
     * @param percent        share of the victim's balance moved to the killer
     * @param cooldownMillis anti-farm: same killer → same victim pays only once per this period
     */
    record PlayerReward(boolean enabled, double percent, long cooldownMillis,
                        Settings.SoundSettings killerSound, Settings.SoundSettings victimSound, List<String> commands) {
        static PlayerReward load(ConfigurationSection s) {
            double percent = s.getDouble("percent", 10);
            if (Double.isNaN(percent) || percent < 0 || percent > 100) {
                throw new IllegalArgumentException(ConfigReader.path(s, "percent") + " must be between 0 and 100");
            }
            return new PlayerReward(
                    s.getBoolean("enabled", true),
                    percent,
                    TimeUnit.SECONDS.toMillis(Math.max(0, s.getLong("cooldown-seconds", 600))),
                    ConfigReader.sound(s, "killer-sound"),
                    ConfigReader.sound(s, "victim-sound"),
                    List.copyOf(s.getStringList("commands"))
            );
        }
    }

    /**
     * @param name      MiniMessage display name for {@code <entity>}; {@code null} uses the client translation
     * @param message   chat message override; {@code null} uses killer-money.mob, empty disables
     * @param actionbar action bar override; {@code null} uses killer-money.mob-actionbar, empty disables
     */
    record MobReward(double min, double max, @Nullable String name, @Nullable String message, @Nullable String actionbar,
                     Settings.SoundSettings sound, List<String> commands) {
        static MobReward load(ConfigurationSection s) {
            String path = ConfigReader.path(s, "reward");
            String raw = s.getString("reward", "").trim();
            double min;
            double max;
            try {
                int dash = raw.indexOf('-', 1);
                if (dash > 0) {
                    min = Double.parseDouble(raw.substring(0, dash).trim().replace(',', '.'));
                    max = Double.parseDouble(raw.substring(dash + 1).trim().replace(',', '.'));
                } else {
                    min = Double.parseDouble(raw.replace(',', '.'));
                    max = min;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(path + " must look like 'min-max' (e.g. 5-50), got '" + raw + "'");
            }
            if (Double.isNaN(min) || Double.isNaN(max) || Double.isInfinite(max) || min < 0 || max < min) {
                throw new IllegalArgumentException(path + ": expected 0 <= min <= max, got '" + raw + "'");
            }
            return new MobReward(min, max, s.getString("name"), s.getString("message"), s.getString("actionbar"),
                    ConfigReader.sound(s, "sound"), List.copyOf(s.getStringList("commands")));
        }
    }

    static KillerMoneyConfig load(YamlConfiguration config) {
        Map<EntityType, MobReward> mobs = new EnumMap<>(EntityType.class);
        ConfigurationSection entities = config.getConfigurationSection("entities");
        if (entities != null) {
            Registry<EntityType> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENTITY_TYPE);
            for (String key : entities.getKeys(false)) {
                NamespacedKey id = NamespacedKey.fromString(key.trim().toLowerCase(Locale.ROOT));
                EntityType type = id == null ? null : registry.get(id);
                if (type == null || type == EntityType.PLAYER || !type.isAlive()) {
                    throw new IllegalArgumentException(ConfigReader.path(entities, key) + ": unknown mob type");
                }
                mobs.put(type, MobReward.load(ConfigReader.section(entities, key)));
            }
        }
        return new KillerMoneyConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("require-permission", true),
                config.getBoolean("ignore-creative", true),
                config.getBoolean("ignore-spawner-mobs", true),
                PlayerReward.load(ConfigReader.section(config, "player")),
                Map.copyOf(mobs)
        );
    }
}
