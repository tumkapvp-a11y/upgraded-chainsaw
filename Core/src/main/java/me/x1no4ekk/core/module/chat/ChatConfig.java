package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.config.Settings;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of chat.yml. Read from the async chat thread, swapped atomically on reload.
 */
record ChatConfig(
        boolean localEnabled,
        String globalSymbol,
        double localRadius,
        boolean notifyNobodyHeard,
        long cooldownMillis,
        long joinDelayMillis,
        String globalFormat,
        String localFormat,
        NameSettings name,
        String guildFormat,
        boolean vaultFallback,
        List<Group> groups,
        Group defaultGroup,
        Channel donate,
        Channel staff,
        PrivateSettings privateMessages,
        int clearLines,
        Map<String, BroadcastSettings> broadcasts,
        AutoBroadcastSettings autoBroadcast) {

    static final List<String> BROADCAST_IDS = List.of("broadcast", "ad", "buy", "sell", "stream", "video");

    enum ClickAction { SUGGEST, RUN, NONE }

    enum LinkMode { URL, LABEL }

    record NameSettings(String hover, ClickAction clickAction, String clickCommand) {

        /**
         * @return click event for the player's name, or {@code null} when disabled
         */
        @Nullable ClickEvent click(String playerName) {
            String command = clickCommand.replace("<name>", playerName);
            return switch (clickAction) {
                case SUGGEST -> ClickEvent.suggestCommand(command);
                case RUN -> ClickEvent.runCommand(command);
                case NONE -> null;
            };
        }
    }

    /**
     * Chat privilege. {@code permission} is empty when only Vault groups select it.
     */
    record Group(String id, String permission, Set<String> vaultGroups, String prefix, String suffix,
                 String nameColor, String messageColor, boolean allowFormatting) {
    }

    record Channel(String format, long cooldownMillis) {
    }

    record PrivateSettings(long cooldownMillis, String formatSender, String formatReceiver, String formatSpy,
                           String formatCommandSpy, Settings.SoundSettings sound) {
    }

    record BroadcastSettings(String id, String permission, String format, long cooldownMillis, double price,
                             boolean parseMiniMessage, boolean respectIgnore, Settings.SoundSettings sound,
                             LinkMode linkMode, String linkLabel) {
    }

    record AutoBroadcastSettings(boolean enabled, int intervalSeconds, boolean random, List<String> messages,
                                 Settings.SoundSettings sound) {
    }

    BroadcastSettings broadcast(String id) {
        return broadcasts.get(id);
    }

    // ---------------------------------------------------------------- parsing

    /**
     * @throws IllegalArgumentException if a value is invalid
     */
    static ChatConfig parse(ConfigurationSection root) {
        ConfigurationSection mode = section(root, "chat-mode");
        String symbol = string(mode, "global-symbol", "!");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("chat-mode.global-symbol must not be empty");
        }
        double radius = mode.getDouble("local-radius");
        if (!(radius > 0)) {
            throw new IllegalArgumentException("chat-mode.local-radius must be greater than 0");
        }

        ConfigurationSection limits = section(root, "limits");
        ConfigurationSection formats = section(root, "formats");
        ConfigurationSection clear = section(root, "chat-clear");
        int clearLines = clear.getInt("lines");
        if (clearLines < 1 || clearLines > 500) {
            throw new IllegalArgumentException("chat-clear.lines must be between 1 and 500");
        }

        return new ChatConfig(
                mode.getBoolean("local-enabled"),
                symbol,
                radius,
                mode.getBoolean("notify-nobody-heard"),
                seconds(limits, "cooldown-seconds"),
                seconds(limits, "join-delay-seconds"),
                text(formats, "global", "<name>: <message>"),
                text(formats, "local", "<name>: <message>"),
                parseName(section(root, "player-name")),
                text(section(root, "guild"), "format", "<tag> "),
                root.getBoolean("vault-fallback"),
                parseGroups(section(root, "groups")),
                parseGroup("default", section(root, "default-group"), ""),
                parseChannel(section(root, "donate-chat")),
                parseChannel(section(root, "staff-chat")),
                parsePrivate(section(root, "private-messages")),
                clearLines,
                parseBroadcasts(section(root, "broadcasts")),
                parseAutoBroadcast(section(root, "auto-broadcast")));
    }

    private static NameSettings parseName(ConfigurationSection s) {
        ConfigurationSection click = section(s, "click");
        String action = string(click, "action", "suggest").toLowerCase(Locale.ROOT);
        ClickAction clickAction = switch (action) {
            case "suggest" -> ClickAction.SUGGEST;
            case "run" -> ClickAction.RUN;
            case "none" -> ClickAction.NONE;
            default -> throw new IllegalArgumentException("player-name.click.action must be suggest, run or none");
        };
        return new NameSettings(text(s, "hover", ""), clickAction, string(click, "command", "/msg <name> "));
    }

    private static List<Group> parseGroups(ConfigurationSection s) {
        List<Group> groups = new ArrayList<>();
        for (String id : s.getKeys(false)) {
            ConfigurationSection group = s.getConfigurationSection(id);
            if (group == null) {
                throw new IllegalArgumentException("groups." + id + " must be a section");
            }
            groups.add(parseGroup(id, group, "core.chat.group." + id.toLowerCase(Locale.ROOT)));
        }
        return List.copyOf(groups);
    }

    private static Group parseGroup(String id, ConfigurationSection s, String defaultPermission) {
        Set<String> vaultGroups = new HashSet<>();
        for (String name : s.getStringList("vault-groups")) {
            if (!name.isBlank()) {
                vaultGroups.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new Group(id,
                string(s, "permission", defaultPermission).trim(),
                Set.copyOf(vaultGroups),
                string(s, "prefix", ""),
                string(s, "suffix", ""),
                string(s, "name-color", ""),
                string(s, "message-color", ""),
                s.getBoolean("allow-formatting", false));
    }

    private static Channel parseChannel(ConfigurationSection s) {
        return new Channel(text(s, "format", "<name>: <message>"), seconds(s, "cooldown-seconds"));
    }

    private static PrivateSettings parsePrivate(ConfigurationSection s) {
        return new PrivateSettings(
                seconds(s, "cooldown-seconds"),
                text(s, "format-sender", "<receiver>: <message>"),
                text(s, "format-receiver", "<sender>: <message>"),
                text(s, "format-spy", "<sender> -> <receiver>: <message>"),
                text(s, "format-command-spy", "<player>: <command>"),
                sound(s, "sound"));
    }

    private static Map<String, BroadcastSettings> parseBroadcasts(ConfigurationSection s) {
        Map<String, BroadcastSettings> result = new LinkedHashMap<>();
        for (String id : BROADCAST_IDS) {
            ConfigurationSection b = section(s, id);
            String linkModeRaw = string(b, "link-mode", "url").toLowerCase(Locale.ROOT);
            LinkMode linkMode = switch (linkModeRaw) {
                case "url" -> LinkMode.URL;
                case "label" -> LinkMode.LABEL;
                default -> throw new IllegalArgumentException("broadcasts." + id + ".link-mode must be url or label");
            };
            double price = b.getDouble("price");
            if (Double.isNaN(price) || Double.isInfinite(price) || price < 0) {
                throw new IllegalArgumentException("broadcasts." + id + ".price must be 0 or greater");
            }
            String defaultPermission = id.equals("broadcast") ? "core.broadcast" : "core.broadcast." + id;
            result.put(id, new BroadcastSettings(
                    id,
                    string(b, "permission", defaultPermission).trim(),
                    text(b, "format", "<name>: <message>"),
                    seconds(b, "cooldown-seconds"),
                    price,
                    b.getBoolean("parse-minimessage"),
                    b.getBoolean("respect-ignore"),
                    sound(b, "sound"),
                    linkMode,
                    string(b, "link-label", "<aqua><u>[Открыть]</u></aqua>")));
        }
        return Map.copyOf(result);
    }

    private static AutoBroadcastSettings parseAutoBroadcast(ConfigurationSection s) {
        int interval = s.getInt("interval-seconds");
        if (interval < 1) {
            throw new IllegalArgumentException("auto-broadcast.interval-seconds must be at least 1");
        }
        String order = string(s, "order", "random").toLowerCase(Locale.ROOT);
        if (!order.equals("random") && !order.equals("sequential")) {
            throw new IllegalArgumentException("auto-broadcast.order must be random or sequential");
        }
        List<String> messages = new ArrayList<>();
        ConfigurationSection list = s.getConfigurationSection("messages");
        if (list != null) {
            for (String key : list.getKeys(false)) {
                String message = text(list, key, "");
                if (!message.isBlank()) {
                    messages.add(message);
                }
            }
        }
        return new AutoBroadcastSettings(s.getBoolean("enabled"), interval, order.equals("random"),
                List.copyOf(messages), sound(s, "sound"));
    }

    // ---------------------------------------------------------------- helpers

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("missing section '" + key + "' in " + describe(parent));
        }
        return section;
    }

    private static String describe(ConfigurationSection section) {
        String path = section.getCurrentPath();
        return path == null || path.isEmpty() ? "root" : path;
    }

    /**
     * String value; the bundled chat.yml (attached as defaults) is consulted before the fallback.
     */
    private static String string(ConfigurationSection s, String key, String fallback) {
        String value = s.getString(key);
        return value != null ? value : fallback;
    }

    /**
     * A single MiniMessage string or a list of lines joined with {@code <newline>}.
     */
    private static String text(ConfigurationSection s, String key, String fallback) {
        if (s.isList(key)) {
            return String.join("<newline>", s.getStringList(key));
        }
        return string(s, key, fallback);
    }

    private static long seconds(ConfigurationSection s, String key) {
        long value = s.getLong(key);
        if (value < 0) {
            throw new IllegalArgumentException(describe(s) + "." + key + " must be 0 or greater");
        }
        return value * 1000L;
    }

    private static Settings.SoundSettings sound(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        if (s == null) {
            return new Settings.SoundSettings("", 1F, 1F);
        }
        return new Settings.SoundSettings(string(s, "key", ""), (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0));
    }
}
