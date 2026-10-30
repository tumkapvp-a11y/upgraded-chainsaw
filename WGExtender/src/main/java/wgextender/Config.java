package wgextender;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import wgextender.features.regionprotect.ownormembased.RestrictCommands.RestrictedCommand;

/**
 * Plugin settings. Keys are compatible with config.yml of WGExtender 3.x.
 * Fields are read on the main thread only, reload also happens there.
 */
public final class Config {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final WGExtender plugin;

    public boolean claimExpandSelectionVertical;

    public boolean claimBlockLimitsEnabled;
    public Map<String, Long> claimBlockLimits = Map.of();
    public long claimBlockLimitDefault;
    public long claimBlockMinimalVolume;
    public long claimBlockMinimalHorizontal;
    public long claimBlockMinimalVertical;

    public boolean checkLavaFlow;
    public boolean checkWaterFlow;
    public boolean checkOtherLiquidFlow;

    public boolean checkFireSpreadToRegion;
    public boolean disableFireSpreadInRegion;
    public boolean disableBlockBurnInRegion;

    public boolean checkExplosionBlockDamage;
    public boolean checkExplosionEntityDamage;

    public boolean claimAutoFlagsEnabled;
    public Map<Flag<?>, String> claimAutoFlags = Map.of();

    public boolean restrictCommandsInRegionEnabled;
    public List<RestrictedCommand> restrictedCommandsInRegion = List.of();

    public boolean extendedWorldEditWandEnabled;

    /** {@code null} - default WorldGuard behaviour, {@code TRUE} - allow, {@code FALSE} - deny. */
    public Boolean miscDefaultPvPFlagOperationMode;

    public Config(WGExtender plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        claimExpandSelectionVertical = config.getBoolean("claim.vertexpand");

        claimBlockLimitsEnabled = config.getBoolean("claim.blocklimits.enabled");
        Map<String, Long> limits = new HashMap<>();
        claimBlockLimitDefault = 0;
        ConfigurationSection limitsSection = config.getConfigurationSection("claim.blocklimits.limits");
        if (limitsSection != null) {
            claimBlockLimitDefault = readLong(limitsSection, "default");
            for (String group : limitsSection.getKeys(false)) {
                limits.put(group.toLowerCase(Locale.ROOT), readLong(limitsSection, group));
            }
        }
        claimBlockLimits = Collections.unmodifiableMap(limits);

        ConfigurationSection minimal = config.getConfigurationSection("claim.blocklimits.minimal");
        claimBlockMinimalVolume = minimal != null ? readLong(minimal, "volume") : 0;
        claimBlockMinimalHorizontal = minimal != null ? readLong(minimal, "horizontal") : 0;
        claimBlockMinimalVertical = minimal != null ? readLong(minimal, "vertical") : 0;

        checkLavaFlow = config.getBoolean("regionprotect.flow.lava");
        checkWaterFlow = config.getBoolean("regionprotect.flow.water");
        checkOtherLiquidFlow = config.getBoolean("regionprotect.flow.other");

        checkFireSpreadToRegion = config.getBoolean("regionprotect.fire.spread.toregion");
        disableFireSpreadInRegion = config.getBoolean("regionprotect.fire.spread.inregion");
        disableBlockBurnInRegion = config.getBoolean("regionprotect.fire.burn");

        checkExplosionBlockDamage = config.getBoolean("regionprotect.explosion.block");
        checkExplosionEntityDamage = config.getBoolean("regionprotect.explosion.entity");

        claimAutoFlagsEnabled = config.getBoolean("autoflags.enabled");
        Map<Flag<?>, String> autoFlags = new LinkedHashMap<>();
        ConfigurationSection autoFlagsSection = config.getConfigurationSection("autoflags.flags");
        if (autoFlagsSection != null) {
            for (String flagName : autoFlagsSection.getKeys(false)) {
                Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), flagName);
                if (flag != null) {
                    autoFlags.put(flag, autoFlagsSection.getString(flagName, ""));
                } else {
                    plugin.getLogger().warning("autoflags: unknown flag '" + flagName + "', skipped");
                }
            }
        }
        claimAutoFlags = Collections.unmodifiableMap(autoFlags);

        restrictCommandsInRegionEnabled = config.getBoolean("restrictcommands.enabled");
        List<RestrictedCommand> restricted = new ArrayList<>();
        for (String entry : config.getStringList("restrictcommands.commands")) {
            RestrictedCommand command = RestrictedCommand.parse(entry);
            if (command != null) {
                restricted.add(command);
            }
        }
        restrictedCommandsInRegion = List.copyOf(restricted);

        extendedWorldEditWandEnabled = config.getBoolean("extendedwewand");

        String pvpMode = config.getString("misc.pvpmode", "default");
        if (pvpMode.equalsIgnoreCase("allow")) {
            miscDefaultPvPFlagOperationMode = Boolean.TRUE;
        } else if (pvpMode.equalsIgnoreCase("deny")) {
            miscDefaultPvPFlagOperationMode = Boolean.FALSE;
        } else {
            miscDefaultPvPFlagOperationMode = null;
        }
    }

    public boolean isLiquidFlowCheckEnabled() {
        return checkLavaFlow || checkWaterFlow || checkOtherLiquidFlow;
    }

    public boolean isFireSpreadCheckEnabled() {
        return checkFireSpreadToRegion || disableFireSpreadInRegion;
    }

    public boolean isExplosionCheckEnabled() {
        return checkExplosionBlockDamage || checkExplosionEntityDamage;
    }

    public boolean isRestrictCommandsEnabled() {
        return restrictCommandsInRegionEnabled && !restrictedCommandsInRegion.isEmpty();
    }

    private long readLong(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return Math.max(0, number.longValue());
        }
        try {
            // Older configs could contain values above Long.MAX_VALUE, they mean "no limit".
            BigInteger parsed = new BigInteger(String.valueOf(value).trim());
            return parsed.signum() < 0 ? 0 : parsed.min(LONG_MAX).longValue();
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid number '" + value + "' at " + section.getCurrentPath() + "." + key + ", using 0");
            return 0;
        }
    }
}
