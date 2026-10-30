package wgextender.features.regionprotect.regionbased;

import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import wgextender.Config;
import wgextender.utils.WGRegionUtils;

/**
 * Stops liquids (and dispensers) from crossing region borders.
 * Registered only when at least one regionprotect.flow option is enabled.
 */
public final class LiquidFlow implements Listener {

    private final Config config;

    public LiquidFlow(Config config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Block source = event.getBlock();
        if (shouldCheckFlow(source)) {
            checkBorder(source, event.getToBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (!shouldCheckDispense(event.getItem().getType())) {
            return;
        }
        Block block = event.getBlock();
        // getBlockData() instead of getState(): no snapshot of the whole dispenser inventory.
        if (block.getBlockData() instanceof Directional directional) {
            checkBorder(block, block.getRelative(directional.getFacing()), event);
        }
    }

    private boolean shouldCheckFlow(Block source) {
        return switch (source.getType()) {
            case LAVA -> config.checkLavaFlow;
            case WATER -> config.checkWaterFlow;
            default -> config.checkOtherLiquidFlow || (config.checkWaterFlow && isWaterSource(source));
        };
    }

    private static boolean isWaterSource(Block block) {
        return switch (block.getType()) {
            case BUBBLE_COLUMN, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> {
                BlockData data = block.getBlockData();
                yield data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
            }
        };
    }

    // "other" keeps the old behaviour (any dispensed item), lava/water additionally cover their buckets.
    private boolean shouldCheckDispense(Material item) {
        if (config.checkOtherLiquidFlow) {
            return true;
        }
        return switch (item) {
            case LAVA_BUCKET -> config.checkLavaFlow;
            case WATER_BUCKET, AXOLOTL_BUCKET, COD_BUCKET, PUFFERFISH_BUCKET, SALMON_BUCKET, TADPOLE_BUCKET, TROPICAL_FISH_BUCKET ->
                config.checkWaterFlow;
            default -> false;
        };
    }

    private static void checkBorder(Block source, Block target, Cancellable event) {
        RegionManager manager = WGRegionUtils.getRegionManager(source.getWorld());
        if (manager == null || manager.size() == 0) {
            return;
        }
        if (!WGRegionUtils.isInTheSameRegionOrWild(manager, source, target)) {
            event.setCancelled(true);
        }
    }
}
