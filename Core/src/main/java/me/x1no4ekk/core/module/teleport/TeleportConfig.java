package me.x1no4ekk.core.module.teleport;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable snapshot of teleports.yml. {@link #parse} throws {@link IllegalArgumentException} on invalid values,
 * so a broken reload keeps the previous snapshot.
 */
public record TeleportConfig(Tpa tpa, Spawn spawn, Rtp rtp) {

    private static final Pattern OPTION_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final int MAX_COORDINATE = 29_999_000;
    private static final int MAX_ATTEMPTS = 50;

    public record Tpa(long expireMillis, long cooldownMillis, String soundKey, float soundVolume, float soundPitch) {
    }

    public record Spawn(boolean teleportNewPlayers, boolean teleportOnJoin, boolean respawnAtSpawn,
                        boolean alwaysRespawnAtSpawn) {
    }

    public record Rtp(RtpGui gui, Set<Material> unsafeBlocks, Map<String, RtpOption> options) {
    }

    public record RtpGui(String title, int rows, boolean fillerEnabled, Material fillerMaterial,
                         String priceFree, String priceFormat,
                         String statusReady, String statusCooldown, String statusNoPermission, String statusNoMoney) {
    }

    public enum RtpType {
        /** Random point in a ring around a fixed center. */
        RANDOM,
        /** Random point in a ring around a random visible player of the world. */
        NEAR_PLAYER
    }

    /**
     * @param permission     required permission, or {@code null} if everyone with core.rtp may use it
     * @param centerAtSpawn  for {@link RtpType#RANDOM}: use the world spawn instead of {@code centerX/centerZ}
     */
    public record RtpOption(String id, int slot, Material material, String name, List<String> lore,
                            String permission, String world, RtpType type,
                            boolean centerAtSpawn, double centerX, double centerZ,
                            int minRadius, int maxRadius, long cooldownMillis, double price, int maxAttempts) {

        public String cooldownKey() {
            return "rtp:" + id;
        }
    }

    public static TeleportConfig parse(ConfigurationSection root) {
        return new TeleportConfig(parseTpa(root), parseSpawn(root), parseRtp(root));
    }

    private static Tpa parseTpa(ConfigurationSection root) {
        int expire = root.getInt("tpa.expire-seconds", 60);
        if (expire < 5 || expire > 3600) {
            throw new IllegalArgumentException("tpa.expire-seconds must be between 5 and 3600");
        }
        int cooldown = root.getInt("tpa.cooldown-seconds", 5);
        if (cooldown < 0) {
            throw new IllegalArgumentException("tpa.cooldown-seconds must not be negative");
        }
        return new Tpa(expire * 1000L, cooldown * 1000L,
                root.getString("tpa.request-sound.key", ""),
                (float) root.getDouble("tpa.request-sound.volume", 1.0),
                (float) root.getDouble("tpa.request-sound.pitch", 1.0));
    }

    private static Spawn parseSpawn(ConfigurationSection root) {
        return new Spawn(
                root.getBoolean("spawn.teleport-new-players", true),
                root.getBoolean("spawn.teleport-on-join", false),
                root.getBoolean("spawn.respawn-at-spawn", true),
                root.getBoolean("spawn.always-respawn-at-spawn", false));
    }

    private static Rtp parseRtp(ConfigurationSection root) {
        int rows = root.getInt("rtp.gui.rows", 5);
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rtp.gui.rows must be between 1 and 6");
        }
        RtpGui gui = new RtpGui(
                root.getString("rtp.gui.title", "Случайная телепортация"),
                rows,
                root.getBoolean("rtp.gui.filler.enabled", true),
                item(root.getString("rtp.gui.filler.material", "GRAY_STAINED_GLASS_PANE"), "rtp.gui.filler.material"),
                root.getString("rtp.gui.price-free", ""),
                root.getString("rtp.gui.price-format", "<amount>"),
                root.getString("rtp.gui.status.ready", ""),
                root.getString("rtp.gui.status.cooldown", ""),
                root.getString("rtp.gui.status.no-permission", ""),
                root.getString("rtp.gui.status.no-money", ""));

        Set<Material> unsafe = EnumSet.noneOf(Material.class);
        for (String name : root.getStringList("rtp.unsafe-blocks")) {
            Material material = Material.matchMaterial(name);
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("rtp.unsafe-blocks: unknown block '" + name + "'");
            }
            unsafe.add(material);
        }

        ConfigurationSection section = root.getConfigurationSection("rtp.options");
        if (section == null) {
            throw new IllegalArgumentException("rtp.options section is missing");
        }
        Map<String, RtpOption> options = new LinkedHashMap<>();
        Set<Integer> slots = new HashSet<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            RtpOption option = parseOption(key, s, rows);
            if (!slots.add(option.slot())) {
                throw new IllegalArgumentException("rtp.options." + key + ".slot " + option.slot() + " is used twice");
            }
            options.put(option.id(), option);
        }
        return new Rtp(gui, Collections.unmodifiableSet(unsafe), Collections.unmodifiableMap(options));
    }

    private static RtpOption parseOption(String key, ConfigurationSection s, int rows) {
        String id = key.toLowerCase(Locale.ROOT);
        String path = "rtp.options." + key;
        if (!OPTION_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(path + ": id may contain only a-z, 0-9, _ and - (up to 32 chars)");
        }
        int slot = s.getInt("slot", -1);
        if (slot < 0 || slot >= rows * 9) {
            throw new IllegalArgumentException(path + ".slot must be inside the menu (0-" + (rows * 9 - 1) + ")");
        }
        String world = s.getString("world", "");
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException(path + ".world is required");
        }
        RtpType type;
        try {
            type = RtpType.valueOf(s.getString("type", "RANDOM").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ".type must be RANDOM or NEAR_PLAYER");
        }

        boolean centerAtSpawn = true;
        double centerX = 0;
        double centerZ = 0;
        String center = s.getString("center", "SPAWN").trim();
        if (!center.equalsIgnoreCase("SPAWN")) {
            String[] parts = center.split("[\\s;]+");
            if (parts.length != 2) {
                throw new IllegalArgumentException(path + ".center must be SPAWN or 'x z'");
            }
            try {
                centerX = Double.parseDouble(parts[0]);
                centerZ = Double.parseDouble(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(path + ".center must be SPAWN or 'x z'");
            }
            if (Math.abs(centerX) > MAX_COORDINATE || Math.abs(centerZ) > MAX_COORDINATE) {
                throw new IllegalArgumentException(path + ".center is outside the world");
            }
            centerAtSpawn = false;
        }

        int minRadius = s.getInt("min-radius", 0);
        int maxRadius = s.getInt("max-radius", 1000);
        if (minRadius < 0 || maxRadius < 1 || maxRadius < minRadius || maxRadius > MAX_COORDINATE) {
            throw new IllegalArgumentException(path + ": need 0 <= min-radius <= max-radius, max-radius >= 1");
        }
        int cooldown = s.getInt("cooldown-seconds", 0);
        if (cooldown < 0) {
            throw new IllegalArgumentException(path + ".cooldown-seconds must not be negative");
        }
        double price = s.getDouble("price", 0);
        if (price < 0 || Double.isNaN(price) || Double.isInfinite(price)) {
            throw new IllegalArgumentException(path + ".price must not be negative");
        }
        int attempts = s.getInt("max-attempts", 10);
        if (attempts < 1 || attempts > MAX_ATTEMPTS) {
            throw new IllegalArgumentException(path + ".max-attempts must be between 1 and " + MAX_ATTEMPTS);
        }
        String permission = s.getString("permission", "core.rtp." + id);
        if (permission != null && permission.isBlank()) {
            permission = null;
        }
        return new RtpOption(id, slot,
                item(s.getString("material", "COMPASS"), path + ".material"),
                s.getString("name", id),
                List.copyOf(s.getStringList("lore")),
                permission, world, type, centerAtSpawn, centerX, centerZ,
                minRadius, maxRadius, cooldown * 1000L, price, attempts);
    }

    private static Material item(String name, String path) {
        Material material = name == null ? null : Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalArgumentException(path + ": unknown item '" + name + "'");
        }
        return material;
    }
}
