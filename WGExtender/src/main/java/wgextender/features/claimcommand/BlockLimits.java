package wgextender.features.claimcommand;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.Locale;
import org.bukkit.entity.Player;
import wgextender.Config;
import wgextender.VaultIntegration;
import wgextender.utils.WEUtils;

public final class BlockLimits {

    private BlockLimits() {
    }

    public static ProcessedClaimInfo processClaimInfo(Config config, Player player) {
        Region selection;
        try {
            selection = WEUtils.getSelection(player);
        } catch (IncompleteRegionException e) {
            // The claim itself reports the missing selection.
            return ProcessedClaimInfo.EMPTY_ALLOW;
        }

        BlockVector3 min = selection.getMinimumPoint();
        BlockVector3 max = selection.getMaximumPoint();
        long sizeX = (long) max.x() - min.x() + 1;
        long sizeY = (long) max.y() - min.y() + 1;
        long sizeZ = (long) max.z() - min.z() + 1;
        long volume = multiplySaturated(multiplySaturated(sizeX, sizeY), sizeZ);

        if (volume > Integer.MAX_VALUE) {
            return new ProcessedClaimInfo(Result.DENY_MAX_VOLUME, volume, Integer.MAX_VALUE);
        }
        if (!config.claimBlockLimitsEnabled || player.hasPermission("worldguard.region.unlimited")) {
            return ProcessedClaimInfo.EMPTY_ALLOW;
        }
        if (volume < config.claimBlockMinimalVolume) {
            return new ProcessedClaimInfo(Result.DENY_MIN_VOLUME, volume, config.claimBlockMinimalVolume);
        }
        long minHorizontal = Math.min(sizeX, sizeZ);
        if (minHorizontal < config.claimBlockMinimalHorizontal) {
            return new ProcessedClaimInfo(Result.DENY_HORIZONTAL, minHorizontal, config.claimBlockMinimalHorizontal);
        }
        if (sizeY < config.claimBlockMinimalVertical) {
            return new ProcessedClaimInfo(Result.DENY_VERTICAL, sizeY, config.claimBlockMinimalVertical);
        }

        String[] groups = VaultIntegration.getPlayerGroups(player);
        if ((groups == null || groups.length == 0) && !config.claimBlockLimitDefaultSet) {
            // Groups are unknown (no Vault) and there is no fallback limit.
            return ProcessedClaimInfo.EMPTY_ALLOW;
        }
        long maxBlocks = config.claimBlockLimitDefault;
        if (groups != null) {
            for (String group : groups) {
                Long limit = config.claimBlockLimits.get(group.toLowerCase(Locale.ROOT));
                if (limit != null && limit > maxBlocks) {
                    maxBlocks = limit;
                }
            }
        }
        if (volume > maxBlocks) {
            return new ProcessedClaimInfo(Result.DENY_MAX_VOLUME, volume, maxBlocks);
        }
        return ProcessedClaimInfo.EMPTY_ALLOW;
    }

    private static long multiplySaturated(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return high != 0 || low < 0 ? Long.MAX_VALUE : low;
    }

    public record ProcessedClaimInfo(Result result, long assignedSize, long assignedLimit) {
        public static final ProcessedClaimInfo EMPTY_ALLOW = new ProcessedClaimInfo(Result.ALLOW, 0, 0);
    }

    public enum Result {
        ALLOW,
        DENY_MAX_VOLUME,
        DENY_MIN_VOLUME,
        DENY_HORIZONTAL,
        DENY_VERTICAL
    }
}
