package wgextender.features.regionprotect.regionbased;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import wgextender.Config;
import wgextender.utils.WGRegionUtils;

public final class Explode implements Listener {

    private final Config config;

    public Explode(Config config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (config.checkExplosionBlockDamage) {
            protectBlocks(event.getEntity().getWorld(), event.blockList(), findExplosionSource(event.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (config.checkExplosionBlockDamage) {
            protectBlocks(event.getBlock().getWorld(), event.blockList(), null);
        }
    }

    private static void protectBlocks(World world, List<Block> blocks, Player source) {
        if (blocks.isEmpty()) {
            return;
        }
        RegionManager manager = WGRegionUtils.getRegionManager(world);
        if (manager == null) {
            return;
        }

        if (source == null) {
            // One index query for the whole explosion; explosions in the wild skip per-block lookups.
            if (hasRegionsAround(manager, blocks)) {
                blocks.removeIf(block -> WGRegionUtils.getRegionsAt(manager, block).size() > 0);
            }
            return;
        }

        LocalPlayer localPlayer = WGRegionUtils.wrapPlayer(source);
        if (WGRegionUtils.canBypassProtection(source, localPlayer)) {
            return;
        }
        if (hasRegionsAround(manager, blocks)) {
            blocks.removeIf(block -> !WGRegionUtils.canBuild(WGRegionUtils.getRegionsAt(manager, block), localPlayer));
        } else if (!WGRegionUtils.canBuild(WGRegionUtils.getRegionsAt(manager, blocks.getFirst()), localPlayer)) {
            // Only __global__ applies here, so the answer is the same for every block.
            blocks.clear();
        }
    }

    private static boolean hasRegionsAround(RegionManager manager, List<Block> blocks) {
        if (manager.size() == 0) {
            return false;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Block block : blocks) {
            int x = block.getX(), y = block.getY(), z = block.getZ();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return WGRegionUtils.hasRegionsIn(manager, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByExplosion(EntityDamageEvent event) {
        if (!config.checkExplosionEntityDamage) {
            return;
        }
        DamageCause cause = event.getCause();
        if (cause != DamageCause.BLOCK_EXPLOSION && cause != DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        Entity entity = event.getEntity();
        RegionManager manager = WGRegionUtils.getRegionManager(entity.getWorld());
        if (manager == null || manager.size() == 0) {
            return;
        }
        ApplicableRegionSet regions = WGRegionUtils.getRegionsAt(manager, entity.getLocation().getBlock());
        if (regions.size() == 0) {
            return;
        }
        Player source = event instanceof EntityDamageByEntityEvent byEntity ? findExplosionSource(byEntity.getDamager()) : null;
        if (source == null) {
            event.setCancelled(true);
            return;
        }
        LocalPlayer localPlayer = WGRegionUtils.wrapPlayer(source);
        if (!WGRegionUtils.canBypassProtection(source, localPlayer) && !WGRegionUtils.canBuild(regions, localPlayer)) {
            event.setCancelled(true);
        }
    }

    private static Player findExplosionSource(Entity exploded) {
        return exploded instanceof TNTPrimed primed && primed.getSource() instanceof Player player ? player : null;
    }
}
