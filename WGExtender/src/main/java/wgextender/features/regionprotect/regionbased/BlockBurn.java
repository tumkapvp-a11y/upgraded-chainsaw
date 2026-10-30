package wgextender.features.regionprotect.regionbased;

import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import wgextender.Config;
import wgextender.utils.WGRegionUtils;

public final class BlockBurn implements Listener {

    private final Config config;

    public BlockBurn(Config config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!config.disableBlockBurnInRegion) {
            return;
        }
        Block block = event.getBlock();
        RegionManager manager = WGRegionUtils.getRegionManager(block.getWorld());
        if (manager != null && manager.size() > 0 && WGRegionUtils.getRegionsAt(manager, block).size() > 0) {
            event.setCancelled(true);
        }
    }
}
