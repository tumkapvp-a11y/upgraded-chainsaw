package me.x1no4ekk.core.module.teleport;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.EnumSet;
import java.util.Set;

/**
 * Safety checks for arrival points. Main thread only; the chunk must already be loaded.
 */
public final class SafeSpots {

    /** Never safe to stand on or in, regardless of configuration. */
    private static final Set<Material> ALWAYS_UNSAFE = EnumSet.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.POWDER_SNOW, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    private SafeSpots() {
    }

    public static boolean isHazard(Material material, Set<Material> extra) {
        return ALWAYS_UNSAFE.contains(material) || extra.contains(material);
    }

    /**
     * Safe arrival point in the column x/z: the surface for normal worlds, the first pocket below the roof
     * for worlds with a ceiling (the Nether).
     *
     * @return block-centered location, or {@code null} if the column has no safe spot
     */
    public static Location find(World world, int x, int z, Set<Material> unsafe) {
        if (world.getEnvironment() == World.Environment.NETHER || world.hasCeiling()) {
            return scanBelowRoof(world, x, z, unsafe);
        }
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (ground.getY() <= world.getMinHeight() || !canStandOn(ground, unsafe)) {
            return null;
        }
        return above(world, ground);
    }

    private static Location scanBelowRoof(World world, int x, int z, Set<Material> unsafe) {
        int top = Math.min(world.getLogicalHeight(), world.getMaxHeight()) - 3;
        for (int y = top; y > world.getMinHeight(); y--) {
            Block ground = world.getBlockAt(x, y, z);
            // Bedrock only forms the roof and floor layers: a pocket there is a trap, not a spot.
            if (ground.getType() != Material.BEDROCK && canStandOn(ground, unsafe)) {
                return above(world, ground);
            }
        }
        return null;
    }

    /**
     * Solid, non-hazardous ground with two free blocks above it.
     */
    public static boolean canStandOn(Block ground, Set<Material> unsafe) {
        Material type = ground.getType();
        if (type.isAir() || !type.isSolid() || ground.isLiquid() || isHazard(type, unsafe)) {
            return false;
        }
        Block feet = ground.getRelative(BlockFace.UP);
        return isFree(feet, unsafe) && isFree(feet.getRelative(BlockFace.UP), unsafe);
    }

    /**
     * Air or a block without collision (grass, flowers) that is neither liquid nor hazardous.
     */
    private static boolean isFree(Block block, Set<Material> unsafe) {
        Material type = block.getType();
        if (type.isAir()) {
            return true;
        }
        return block.isPassable() && !block.isLiquid() && !isHazard(type, unsafe);
    }

    private static Location above(World world, Block ground) {
        return new Location(world, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
    }
}
