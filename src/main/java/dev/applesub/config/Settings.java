package dev.applesub.config;

import dev.applesub.AppleSubPlugin;
import dev.applesub.data.Feature;
import dev.applesub.util.DurationUtil;
import dev.applesub.util.Items;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class Settings {

    private static final String DEFAULT_DATE_FORMAT = "dd.MM.yyyy HH:mm";
    private static final long DEFAULT_DURATION = TimeUnit.DAYS.toMillis(30);

    private final AppleSubPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();

    private FileConfiguration config;
    private Component prefix = Component.empty();
    private long defaultDuration = DEFAULT_DURATION;
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
    private boolean defaultNightVision;
    private boolean defaultAntiFire;
    private boolean defaultAntiTrash;
    private boolean defaultArmorNotify;
    private final List<Material> defaultTrash = new ArrayList<>();
    private final Set<Material> defaultTrashSet = EnumSet.noneOf(Material.class);
    private int maxCustomTrash;
    private int armorThresholdPercent;
    private long armorCooldownMillis;
    private boolean armorTitle;
    private final Map<EquipmentSlot, String> slotNames = new EnumMap<>(EquipmentSlot.class);
    private final Map<String, Sound> sounds = new HashMap<>();

    public Settings(AppleSubPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        Logger log = plugin.getLogger();

        prefix = mini.deserialize(config.getString("messages.prefix", ""));

        String rawDuration = config.getString("subscription.default-duration", "30d");
        defaultDuration = DurationUtil.parse(rawDuration).orElseGet(() -> {
            log.warning("Неверный subscription.default-duration: " + rawDuration + ", используется 30d");
            return DEFAULT_DURATION;
        });
        try {
            dateFormatter = DateTimeFormatter.ofPattern(config.getString("subscription.date-format", DEFAULT_DATE_FORMAT));
        } catch (IllegalArgumentException e) {
            log.warning("Неверный subscription.date-format, используется " + DEFAULT_DATE_FORMAT);
            dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
        }

        defaultNightVision = config.getBoolean("defaults.night-vision", true);
        defaultAntiFire = config.getBoolean("defaults.anti-fire", true);
        defaultAntiTrash = config.getBoolean("defaults.anti-trash", false);
        defaultArmorNotify = config.getBoolean("defaults.armor-notify", true);

        defaultTrash.clear();
        defaultTrashSet.clear();
        for (String name : config.getStringList("anti-trash.default-items")) {
            Material material = Material.matchMaterial(name);
            if (material == null || material.isAir() || !material.isItem()) {
                log.warning("anti-trash.default-items: неизвестный предмет " + name);
                continue;
            }
            if (defaultTrashSet.add(material)) {
                defaultTrash.add(material);
            }
        }
        maxCustomTrash = Math.max(0, config.getInt("anti-trash.max-custom-items", 45));

        armorThresholdPercent = Math.clamp(config.getInt("armor-notify.threshold-percent", 15), 1, 100);
        armorCooldownMillis = Math.max(0L, config.getLong("armor-notify.cooldown-seconds", 60)) * 1000L;
        armorTitle = config.getBoolean("armor-notify.title", true);
        slotNames.clear();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            slotNames.put(slot, config.getString("armor-notify.slot-names." + slot.name(), slot.name()));
        }

        sounds.clear();
        ConfigurationSection soundSection = config.getConfigurationSection("sounds");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                Sound sound = parseSound(key, soundSection.getConfigurationSection(key));
                if (sound != null) {
                    sounds.put(key, sound);
                }
            }
        }
    }

    private Sound parseSound(String name, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String key = section.getString("key", "");
        if (key.isBlank()) {
            return null;
        }
        try {
            return Sound.sound(Key.key(key.toLowerCase(Locale.ROOT)), Sound.Source.MASTER,
                    (float) section.getDouble("volume", 1.0), (float) section.getDouble("pitch", 1.0));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("sounds." + name + ": неверный ключ звука " + key);
            return null;
        }
    }

    // ---- messages ----

    public Component message(String key, TagResolver... resolvers) {
        String raw = config.getString("messages." + key);
        return raw == null || raw.isEmpty() ? null : mini.deserialize(raw, resolvers);
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        Component component = message(key, resolvers);
        if (component != null) {
            audience.sendMessage(prefix.append(component));
        }
    }

    public void sendLines(Audience audience, String key, TagResolver... resolvers) {
        for (String line : config.getStringList("messages." + key)) {
            audience.sendMessage(mini.deserialize(line, resolvers));
        }
    }

    public void actionBar(Player player, String key, TagResolver... resolvers) {
        Component component = message(key, resolvers);
        if (component != null) {
            player.sendActionBar(component);
        }
    }

    public void title(Player player, String titleKey, String subtitleKey, TagResolver... resolvers) {
        Component title = message(titleKey, resolvers);
        Component subtitle = message(subtitleKey, resolvers);
        if (title == null && subtitle == null) {
            return;
        }
        player.showTitle(Title.title(
                title == null ? Component.empty() : title,
                subtitle == null ? Component.empty() : subtitle,
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(500))));
    }

    public void sound(Player player, String key) {
        Sound sound = sounds.get(key);
        if (sound != null) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    // ---- menu texts ----

    public Component menuText(String path, TagResolver... resolvers) {
        return Items.noItalic(mini.deserialize(config.getString("menu." + path, ""), resolvers));
    }

    public List<Component> menuLore(String path, TagResolver... resolvers) {
        List<String> lines = config.getStringList("menu." + path);
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(Items.noItalic(mini.deserialize(line, resolvers)));
        }
        return lore;
    }

    public Material menuMaterial(String path, Material fallback) {
        Material material = Material.matchMaterial(config.getString("menu." + path, ""));
        return material == null || !material.isItem() || material.isAir() ? fallback : material;
    }

    public Component state(boolean enabled) {
        return menuText(enabled ? "state-on" : "state-off");
    }

    public Component featureName(Feature feature) {
        return menuText("main.features." + feature.key() + ".name");
    }

    // ---- time ----

    public String formatDuration(long millis) {
        if (millis == DurationUtil.FOREVER) {
            return config.getString("time.forever", "навсегда");
        }
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        long secs = seconds % 60;
        StringJoiner joiner = new StringJoiner(" ");
        if (days > 0) {
            joiner.add(days + unit("day"));
        }
        if (hours > 0) {
            joiner.add(hours + unit("hour"));
        }
        if (minutes > 0) {
            joiner.add(minutes + unit("minute"));
        }
        if (days == 0 && hours == 0 && (secs > 0 || minutes == 0)) {
            joiner.add(secs + unit("second"));
        }
        return joiner.toString();
    }

    public String formatRemaining(long expiresAt) {
        if (expiresAt == DurationUtil.FOREVER) {
            return formatDuration(DurationUtil.FOREVER);
        }
        return formatDuration(expiresAt - System.currentTimeMillis());
    }

    public String formatDate(long expiresAt) {
        if (expiresAt == DurationUtil.FOREVER) {
            return config.getString("time.forever", "навсегда");
        }
        return dateFormatter.format(Instant.ofEpochMilli(expiresAt).atZone(ZoneId.systemDefault()));
    }

    private String unit(String key) {
        return config.getString("time." + key, "");
    }

    // ---- values ----

    public long defaultDuration() {
        return defaultDuration;
    }

    public boolean defaultNightVision() {
        return defaultNightVision;
    }

    public boolean defaultAntiFire() {
        return defaultAntiFire;
    }

    public boolean defaultAntiTrash() {
        return defaultAntiTrash;
    }

    public boolean defaultArmorNotify() {
        return defaultArmorNotify;
    }

    public List<Material> defaultTrash() {
        return Collections.unmodifiableList(defaultTrash);
    }

    public boolean isDefaultTrash(Material material) {
        return defaultTrashSet.contains(material);
    }

    public int maxCustomTrash() {
        return maxCustomTrash;
    }

    public int armorThresholdPercent() {
        return armorThresholdPercent;
    }

    public long armorCooldownMillis() {
        return armorCooldownMillis;
    }

    public boolean armorTitle() {
        return armorTitle;
    }

    public String slotName(EquipmentSlot slot) {
        return slotNames.getOrDefault(slot, slot.name());
    }
}
