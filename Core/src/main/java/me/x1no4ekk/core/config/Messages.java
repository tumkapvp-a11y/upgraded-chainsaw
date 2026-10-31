package me.x1no4ekk.core.config;

import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * MiniMessage-based messages from messages.yml. Thread-safe: the backing map is immutable and swapped on reload.
 */
public final class Messages {

    private final Logger logger;
    private volatile Map<String, String> templates = Map.of();
    private volatile TagResolver prefix = TagResolver.empty();

    public Messages(Logger logger) {
        this.logger = logger;
    }

    public void load(YamlConfiguration config) {
        Map<String, String> loaded = new HashMap<>();
        flatten(config, "", loaded);
        // Defaults from the bundled file fill keys removed by the server owner.
        if (config.getDefaults() != null) {
            Map<String, String> defaults = new HashMap<>();
            flatten(config.getDefaults(), "", defaults);
            defaults.forEach(loaded::putIfAbsent);
        }
        String prefixTemplate = loaded.getOrDefault("prefix", "");
        this.prefix = Placeholder.component("prefix", Text.parse(prefixTemplate));
        this.templates = Map.copyOf(loaded);
    }

    private static void flatten(ConfigurationSection section, String path, Map<String, String> out) {
        for (String key : section.getKeys(false)) {
            String fullKey = path.isEmpty() ? key : path + "." + key;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    flatten(child, fullKey, out);
                }
            } else if (section.isList(key)) {
                List<String> lines = section.getStringList(key);
                out.put(fullKey, String.join("<newline>", lines));
            } else {
                String value = section.getString(key);
                if (value != null) {
                    out.put(fullKey, value);
                }
            }
        }
    }

    public Component get(String key, TagResolver... resolvers) {
        String template = templates.get(key);
        if (template == null) {
            logger.warning("Missing message key: " + key);
            return Component.text(key);
        }
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        all[0] = prefix;
        System.arraycopy(resolvers, 0, all, 1, resolvers.length);
        return Text.parse(template, all);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String template = templates.get(key);
        if (template != null && template.isEmpty()) {
            return; // an empty message disables it
        }
        sender.sendMessage(get(key, resolvers));
    }
}
