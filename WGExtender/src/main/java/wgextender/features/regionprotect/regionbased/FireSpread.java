package wgextender.features.regionprotect.regionbased;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockSpreadEvent;
import wgextender.Config;
import wgextender.utils.WGRegionUtils;

public final class FireSpread implements Listener {

    private final Config config;

    public FireSpread(Config config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockIgniteBySpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() != Material.FIRE) {
            return;
        }
        Block source = event.getSource();
        RegionManager manager = WGRegionUtils.getRegionManager(source.getWorld());
        if (manager == null || manager.size() == 0) {
            return;
        }

        // Both checks share the same two lookups instead of doing four.
        Set<ProtectedRegion> sourceRegions = WGRegionUtils.getRegionsAt(manager, source).getRegions();
        Set<ProtectedRegion> targetRegions = WGRegionUtils.getRegionsAt(manager, event.getBlock()).getRegions();
        boolean sameRegions = sourceRegions.equals(targetRegions);

        if (config.checkFireSpreadToRegion && !sameRegions) {
            event.setCancelled(true);
        } else if (config.disableFireSpreadInRegion && sameRegions && !sourceRegions.isEmpty()) {
            event.setCancelled(true);
        }
    }
}
