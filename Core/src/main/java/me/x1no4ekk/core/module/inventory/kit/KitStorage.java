package me.x1no4ekk.core.module.inventory.kit;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.config.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads kits.yml and applies /kit create and /kit delete to it. Edits are validated by parsing the result
 * before the file is written, so an edit can never leave an unloadable file behind. Comments are preserved.
 */
public final class KitStorage {

    public static final String FILE_NAME = "kits.yml";

    private final CorePlugin plugin;

    public KitStorage(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public KitsConfig load() throws IOException, InvalidConfigurationException {
        return KitsConfig.parse(ConfigFile.load(plugin, FILE_NAME));
    }

    /**
     * Adds a kit whose items are stored as Base64 entries.
     *
     * @return the configuration after the change
     */
    public KitsConfig create(String id, int slot, long cooldownSeconds, Material icon, List<ItemStack> items)
            throws IOException, InvalidConfigurationException {
        return edit(yaml -> {
            String base = "kits." + id + ".";
            yaml.set(base + "display-name", "<gold>" + id);
            yaml.set(base + "slot", slot);
            yaml.set(base + "icon.material", icon.name());
            yaml.set(base + "icon.lore", List.of());
            yaml.set(base + "cooldown-seconds", cooldownSeconds);
            yaml.set(base + "price", 0);
            yaml.set(base + "auto-equip", true);
            yaml.set(base + "first-join", false);
            yaml.set(base + "items", items.stream().map(KitItems::encode).toList());
        });
    }

    /**
     * @return the configuration after the change, or {@code null} if the kit is not defined in the server's file
     */
    public @Nullable KitsConfig delete(String id) throws IOException, InvalidConfigurationException {
        YamlConfiguration current = readFile();
        String key = fileKey(current, id);
        if (key == null) {
            return null;
        }
        return edit(yaml -> yaml.set("kits." + key, null));
    }

    private KitsConfig edit(Consumer<YamlConfiguration> change) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = readFile();
        change.accept(yaml);
        yaml.setDefaults(bundledDefaults());
        KitsConfig parsed = KitsConfig.parse(yaml);
        // Defaults are attached only for validation; save() writes the file's own values.
        yaml.save(file());
        return parsed;
    }

    private YamlConfiguration readFile() throws IOException, InvalidConfigurationException {
        File file = file();
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    private YamlConfiguration bundledDefaults() throws IOException {
        try (InputStream in = plugin.getResource(FILE_NAME)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    private static @Nullable String fileKey(YamlConfiguration yaml, String id) {
        ConfigurationSection kits = yaml.getConfigurationSection("kits");
        if (kits == null) {
            return null;
        }
        for (String key : kits.getKeys(false)) {
            if (key.equalsIgnoreCase(id)) {
                return key;
            }
        }
        return null;
    }
}
