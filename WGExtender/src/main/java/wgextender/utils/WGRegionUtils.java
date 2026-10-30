package wgextender.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.regions.RegionQuery.QueryOption;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WGRegionUtils {

    private static RegionQuery query;

    private WGRegionUtils() {
    }

    public static WorldGuardPlatform getPlatform() {
        return WorldGuard.getInstance().getPlatform();
    }

    public static RegionContainer getRegionContainer() {
        return getPlatform().getRegionContainer();
    }

    /**
     * Shared query object: it uses WorldGuard's short-lived location cache.
     */
    public static RegionQuery getQuery() {
        RegionQuery current = query;
        if (current == null) {
            current = query = getRegionContainer().createQuery();
        }
        return current;
    }

    public static LocalPlayer wrapPlayer(Player player) {
        return WorldGuardPlugin.inst().wrapPlayer(player);
    }

    /**
     * Wraps the sender into an actor that passes every WorldGuard permission check.
     *
     * @param silent drop all chat output produced by WorldGuard for this actor
     */
    public static Actor wrapAsPrivileged(CommandSender sender, boolean silent) {
        Actor actor = sender instanceof Player player ? wrapPlayer(player) : WorldGuardPlugin.inst().wrapCommandSender(sender);
        // getInterfaces() only returns directly declared ones, the console actor declares none itself.
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> type = actor.getClass(); type != null; type = type.getSuperclass()) {
            interfaces.addAll(Set.of(type.getInterfaces()));
        }
        return (Actor) Proxy.newProxyInstance(actor.getClass().getClassLoader(), interfaces.toArray(Class<?>[]::new), (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("hasPermission")) {
                return true;
            }
            if (name.equals("checkPermission")) {
                return null;
            }
            if (silent && name.startsWith("print") && method.getReturnType() == void.class) {
                return null;
            }
            try {
                return method.invoke(actor, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    /**
     * @return region manager, or {@code null} if regions are disabled ({@code use-regions: false}) or not loaded in this world
     */
    public static RegionManager getRegionManager(World world) {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        if (!getPlatform().getGlobalStateManager().get(weWorld).useRegions) {
            return null;
        }
        return getRegionContainer().get(weWorld);
    }

    public static BukkitWorldConfiguration getWorldConfig(World world) {
        return (BukkitWorldConfiguration) getPlatform().getGlobalStateManager().get(BukkitAdapter.adapt(world));
    }

    public static boolean canBypassProtection(Player player) {
        return getPlatform().getSessionManager().hasBypass(wrapPlayer(player), BukkitAdapter.adapt(player.getWorld()));
    }

    public static boolean canBypassProtection(Player player, LocalPlayer localPlayer) {
        return getPlatform().getSessionManager().hasBypass(localPlayer, BukkitAdapter.adapt(player.getWorld()));
    }

    public static ApplicableRegionSet getRegionsAt(Location location) {
        return getQuery().getApplicableRegions(BukkitAdapter.adapt(location));
    }

    public static boolean isInWGRegion(Location location) {
        return getRegionsAt(location).size() > 0;
    }

    public static boolean isFlagAllows(Player player, Location location, StateFlag flag) {
        return getQuery().testState(BukkitAdapter.adapt(location), wrapPlayer(player), flag);
    }

    public static boolean isFlagTrue(Location location, BooleanFlag flag) {
        return Boolean.TRUE.equals(getQuery().queryValue(BukkitAdapter.adapt(location), null, flag));
    }

    /**
     * Region set for a block, straight from the region index. Skips Location adaptation and cache
     * bookkeeping, which matters for very frequent events like liquid flow.
     */
    public static ApplicableRegionSet getRegionsAt(RegionManager manager, Block block) {
        return manager.getApplicableRegions(BlockVector3.at(block.getX(), block.getY(), block.getZ()));
    }

    public static ApplicableRegionSet getRegionsAt(RegionManager manager, Location location) {
        return manager.getApplicableRegions(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    public static boolean canBuild(ApplicableRegionSet set, LocalPlayer player) {
        return set.testState(player, Flags.BUILD);
    }

    /**
     * @return true if any region (except __global__) intersects the given box
     */
    public static boolean hasRegionsIn(RegionManager manager, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        ProtectedCuboidRegion box = new ProtectedCuboidRegion(
            "wgextender-box", true, BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ)
        );
        return manager.getApplicableRegions(box, QueryOption.NONE).size() > 0;
    }

    /**
     * @return true if both locations have exactly the same regions (including both being in the wild)
     */
    public static boolean isInTheSameRegionOrWild(RegionManager manager, Block first, Block second) {
        return getRegionsAt(manager, first).getRegions().equals(getRegionsAt(manager, second).getRegions());
    }
}
