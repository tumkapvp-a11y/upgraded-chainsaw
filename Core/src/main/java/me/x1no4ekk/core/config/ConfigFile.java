package me.x1no4ekk.core.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Loads a YAML file from the data folder, creating it from the jar on first run.
 * The bundled copy is attached as defaults so missing keys fall back to sane values.
 */
public final class ConfigFile {

    private ConfigFile() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String name) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);

        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    config.setDefaults(YamlConfiguration.loadConfiguration(reader));
                }
            }
        }
        return config;
    }
}
