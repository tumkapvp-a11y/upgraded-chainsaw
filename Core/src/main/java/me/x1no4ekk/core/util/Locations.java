package me.x1no4ekk.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Compact location (de)serialization: {@code world;x;y;z;yaw;pitch}.
 * Only the world name is stored so no strong {@link World} references are kept.
 */
public final class Locations {

    private static final char SEPARATOR = ';';

    private Locations() {
    }

    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName() + SEPARATOR
                + format(location.getX()) + SEPARATOR
                + format(location.getY()) + SEPARATOR
                + format(location.getZ()) + SEPARATOR
                + format(location.getYaw()) + SEPARATOR
                + format(location.getPitch());
    }

    /**
     * @return the location, or {@code null} if the input is malformed or the world is not loaded
     */
    public static Location deserialize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] parts = input.split(String.valueOf(SEPARATOR));
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
