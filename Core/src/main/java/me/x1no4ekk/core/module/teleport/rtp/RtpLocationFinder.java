package me.x1no4ekk.core.module.teleport.rtp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.module.teleport.SafeSpots;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Non-blocking search for a safe random point: picks x/z inside the world border, loads (or generates) the
 * chunk asynchronously, checks the column on the main thread and retries by chaining futures.
 */
final class RtpLocationFinder {

    /** Random x/z picks per attempt before giving up on finding one inside the world border. */
    private static final int BORDER_PICKS = 16;
    /** Distance kept from the world border edge. */
    private static final double BORDER_MARGIN = 16;

    private record Search(UUID worldId, double centerX, double centerZ, int minRadius, int maxRadius,
                          Set<Material> unsafe) {
    }

    private final CorePlugin plugin;

    RtpLocationFinder(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Main thread only. The future completes on the main thread with a safe location, or {@code null}
     * when every attempt failed.
     */
    CompletableFuture<Location> find(World world, double centerX, double centerZ, int minRadius, int maxRadius,
                                     int attempts, Set<Material> unsafe) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        attempt(new Search(world.getUID(), centerX, centerZ, minRadius, maxRadius, unsafe), attempts, result);
        return result;
    }

    private void attempt(Search search, int remaining, CompletableFuture<Location> result) {
        World world = Bukkit.getWorld(search.worldId());
        if (world == null || remaining <= 0) {
            result.complete(null);
            return;
        }
        int[] point = pickPoint(world, search);
        if (point == null) {
            attempt(search, remaining - 1, result);
            return;
        }
        int x = point[0];
        int z = point[1];
        world.getChunkAtAsync(x >> 4, z >> 4, true).whenCompleteAsync((chunk, error) -> {
            if (result.isDone()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "RTP: failed to load chunk at " + x + ", " + z, error);
                attempt(search, remaining - 1, result);
                return;
            }
            World loaded = Bukkit.getWorld(search.worldId());
            Location safe = loaded == null ? null : SafeSpots.find(loaded, x, z, search.unsafe());
            if (safe != null) {
                result.complete(safe);
            } else {
                attempt(search, remaining - 1, result);
            }
        }, plugin.mainThread());
    }

    /**
     * Uniform random point in the ring [minRadius, maxRadius] around the center, inside the world border.
     *
     * @return {x, z} or {@code null} if no pick landed inside the border
     */
    private static int[] pickPoint(World world, Search search) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        WorldBorder border = world.getWorldBorder();
        Location borderCenter = border.getCenter();
        double half = border.getSize() / 2 - BORDER_MARGIN;
        double minSquared = (double) search.minRadius() * search.minRadius();
        double maxSquared = (double) search.maxRadius() * search.maxRadius();
        for (int i = 0; i < BORDER_PICKS; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = Math.sqrt(minSquared + random.nextDouble() * (maxSquared - minSquared));
            int x = (int) Math.floor(search.centerX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(search.centerZ() + Math.sin(angle) * distance);
            if (Math.abs(x + 0.5 - borderCenter.getX()) <= half && Math.abs(z + 0.5 - borderCenter.getZ()) <= half) {
                return new int[]{x, z};
            }
        }
        return null;
    }
}
