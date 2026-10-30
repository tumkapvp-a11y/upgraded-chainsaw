package wgextender.utils;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.world.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class WEUtils {

    private static WorldEditPlugin worldEdit;

    private WEUtils() {
    }

    public static WorldEditPlugin getWorldEditPlugin() {
        WorldEditPlugin plugin = worldEdit;
        if (plugin == null || !plugin.isEnabled()) {
            plugin = worldEdit = JavaPlugin.getPlugin(WorldEditPlugin.class);
        }
        return plugin;
    }

    public static Region getSelection(Player player) throws IncompleteRegionException {
        return getWorldEditPlugin().getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
    }

    /**
     * Expands the player's selection to the full world height.
     *
     * @return true if the player had a complete selection
     */
    public static boolean expandVert(Player player) {
        LocalSession session = getWorldEditPlugin().getSession(player);
        World world = BukkitAdapter.adapt(player.getWorld());
        try {
            Region region = session.getSelection(world);
            int height = world.getMaxY() - world.getMinY() + 1;
            region.expand(BlockVector3.at(0, height, 0), BlockVector3.at(0, -height, 0));
            session.getRegionSelector(world).learnChanges();
            return true;
        } catch (IncompleteRegionException | RegionOperationException e) {
            return false;
        }
    }
}
