package me.x1no4ekk.core.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable home point. Stores the world by name so unloaded worlds are never pinned in memory.
 */
public record Home(String name, String world, double x, double y, double z, float yaw, float pitch, long createdAt) {

    public static Home of(String name, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new Home(name, world.getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), System.currentTimeMillis());
    }

    /**
     * @return the location, or {@code null} if the world is not loaded
     */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public World.Environment environment() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? World.Environment.CUSTOM : bukkitWorld.getEnvironment();
    }
}
