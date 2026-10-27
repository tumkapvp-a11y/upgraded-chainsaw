package dev.applesub.data;

import dev.applesub.config.Settings;
import dev.applesub.storage.Database;
import dev.applesub.util.DurationUtil;
import org.bukkit.Material;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached state of an online player. Fields are volatile because the anti-fire
 * packet handler reads them from Netty threads.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String name;
    private volatile long expiresAt;
    private volatile boolean nightVision;
    private volatile boolean antiFire;
    private volatile boolean antiTrash;
    private volatile boolean armorNotify;
    private final Set<Material> customTrash = ConcurrentHashMap.newKeySet();

    public PlayerData(UUID uuid, String name, long expiresAt,
                      boolean nightVision, boolean antiFire, boolean antiTrash, boolean armorNotify) {
        this.uuid = uuid;
        this.name = name;
        this.expiresAt = expiresAt;
        this.nightVision = nightVision;
        this.antiFire = antiFire;
        this.antiTrash = antiTrash;
        this.armorNotify = armorNotify;
    }

    public static PlayerData defaults(UUID uuid, String name, Settings settings) {
        return new PlayerData(uuid, name, 0L, settings.defaultNightVision(), settings.defaultAntiFire(),
                settings.defaultAntiTrash(), settings.defaultArmorNotify());
    }

    public static PlayerData of(Database.PlayerRow row, String name) {
        return new PlayerData(row.uuid(), name, row.expiresAt(), row.nightVision(), row.antiFire(),
                row.antiTrash(), row.armorNotify());
    }

    public Database.PlayerRow snapshot() {
        return new Database.PlayerRow(uuid, name, expiresAt, nightVision, antiFire, antiTrash, armorNotify);
    }

    public boolean hasSubscription() {
        return DurationUtil.isActive(expiresAt, System.currentTimeMillis());
    }

    /** Subscription is active and the feature is switched on. */
    public boolean isActive(Feature feature) {
        return isEnabled(feature) && hasSubscription();
    }

    public boolean isEnabled(Feature feature) {
        return switch (feature) {
            case NIGHT_VISION -> nightVision;
            case ANTI_FIRE -> antiFire;
            case ANTI_TRASH -> antiTrash;
            case ARMOR_NOTIFY -> armorNotify;
        };
    }

    public void setEnabled(Feature feature, boolean enabled) {
        switch (feature) {
            case NIGHT_VISION -> nightVision = enabled;
            case ANTI_FIRE -> antiFire = enabled;
            case ANTI_TRASH -> antiTrash = enabled;
            case ARMOR_NOTIFY -> armorNotify = enabled;
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public void expiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Set<Material> customTrash() {
        return customTrash;
    }
}
