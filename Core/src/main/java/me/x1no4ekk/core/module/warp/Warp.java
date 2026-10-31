package me.x1no4ekk.core.module.warp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Immutable warp snapshot. The world is kept by name so unloaded worlds are never pinned in memory.
 *
 * @param owner     owner UUID, {@code null} for server warps
 * @param ownerName owner name known when the warp was loaded or created (may be stale or {@code null})
 * @param icon      icon material, {@code null} if the stored key is unknown (the configured default is shown)
 * @param rating    average grade (0 when {@code ratings == 0})
 * @param ratings   number of grades
 */
public record Warp(String name, @Nullable UUID owner, @Nullable String ownerName, boolean system,
                   String world, double x, double y, double z, float yaw, float pitch,
                   @Nullable Material icon, @Nullable String description, int visits, long createdAt,
                   double rating, int ratings) {

    static Warp create(String name, @Nullable UUID owner, @Nullable String ownerName, boolean system,
                       Location location, Material icon) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new Warp(name, owner, ownerName, system, world.getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), icon, null, 0, System.currentTimeMillis(), 0, 0);
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    String key() {
        return key(name);
    }

    public boolean isOwnedBy(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    /**
     * Same stored warp (not a later warp re-created under the same name).
     */
    boolean sameAs(Warp other) {
        return other != null && createdAt == other.createdAt && key().equals(other.key());
    }

    /**
     * @return the location, or {@code null} if the world is not loaded
     */
    public @Nullable Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    Warp withVisits(int newVisits) {
        return new Warp(name, owner, ownerName, system, world, x, y, z, yaw, pitch, icon, description,
                Math.max(0, newVisits), createdAt, rating, ratings);
    }

    Warp withRating(double newRating, int newRatings) {
        return new Warp(name, owner, ownerName, system, world, x, y, z, yaw, pitch, icon, description,
                visits, createdAt, newRating, newRatings);
    }
}
