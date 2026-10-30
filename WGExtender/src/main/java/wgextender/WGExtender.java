package wgextender;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import wgextender.commands.Commands;
import wgextender.features.claimcommand.WGRegionCommandWrapper;
import wgextender.features.extendedwand.WEWand;
import wgextender.features.extendedwand.WEWandCommandWrapper;
import wgextender.features.extendedwand.WEWandListener;
import wgextender.features.flags.ChorusFruitFlagHandler;
import wgextender.features.flags.OldPVPAttackSpeedHandler;
import wgextender.features.flags.OldPVPFlagsHandler;
import wgextender.features.flags.WGExtenderFlags;
import wgextender.features.regionprotect.ownormembased.PvPHandlingListener;
import wgextender.features.regionprotect.ownormembased.RestrictCommands;
import wgextender.features.regionprotect.regionbased.BlockBurn;
import wgextender.features.regionprotect.regionbased.Explode;
import wgextender.features.regionprotect.regionbased.FireSpread;
import wgextender.features.regionprotect.regionbased.LiquidFlow;

public final class WGExtender extends JavaPlugin {

    private static WGExtender instance;

    private final List<ToggleableListener> toggleableListeners = new ArrayList<>();
    private Config config;
    private PvPHandlingListener pvpListener;
    private WGRegionCommandWrapper regionCommandWrapper;
    private WEWandCommandWrapper wandCommandWrapper;
    private OldPVPAttackSpeedHandler.Registration attackSpeedHandler;

    public static WGExtender getInstance() {
        return instance;
    }

    public WGExtender() {
        instance = this;
    }

    @Override
    public void onLoad() {
        // WorldGuard only accepts new flags before it enables, so this has to happen in onLoad.
        WGExtenderFlags.registerOnLoad(getLogger());
    }

    @Override
    public void onEnable() {
        WGExtenderFlags.registerLateIfNeeded(getLogger());

        config = new Config(this);
        config.load();

        WEWand.init(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new WEWandListener(), this);
        pm.registerEvents(new ChorusFruitFlagHandler(), this);
        pm.registerEvents(new OldPVPFlagsHandler(this), this);

        // Listeners for disabled features are not registered at all, so they cost nothing on hot events.
        toggleableListeners.add(new ToggleableListener(new LiquidFlow(config), Config::isLiquidFlowCheckEnabled));
        toggleableListeners.add(new ToggleableListener(new FireSpread(config), Config::isFireSpreadCheckEnabled));
        toggleableListeners.add(new ToggleableListener(new BlockBurn(config), c -> c.disableBlockBurnInRegion));
        toggleableListeners.add(new ToggleableListener(new Explode(config), Config::isExplosionCheckEnabled));
        toggleableListeners.add(new ToggleableListener(new RestrictCommands(config), Config::isRestrictCommandsEnabled));

        attackSpeedHandler = OldPVPAttackSpeedHandler.register(this);
        pvpListener = new PvPHandlingListener(config);
        regionCommandWrapper = new WGRegionCommandWrapper(config);
        wandCommandWrapper = new WEWandCommandWrapper(config);

        Commands commands = new Commands(this);
        var command = Objects.requireNonNull(getCommand("wgextender"), "command wgextender is missing in plugin.yml");
        command.setExecutor(commands);
        command.setTabCompleter(commands);

        // WorldEdit and WorldGuard register their commands in onEnable, which already ran (hard depend).
        tryInject("/region claim", () -> regionCommandWrapper.inject());
        applySettings();
    }

    @Override
    public void onDisable() {
        tryRun("WorldGuard PvP listener", () -> { if (pvpListener != null) pvpListener.uninject(); });
        tryRun("/region wrapper", () -> { if (regionCommandWrapper != null) regionCommandWrapper.uninject(); });
        tryRun("//wand wrapper", () -> { if (wandCommandWrapper != null) wandCommandWrapper.uninject(); });
        tryRun("old PvP attack speed", () -> { if (attackSpeedHandler != null) attackSpeedHandler.unregister(); });
        HandlerList.unregisterAll(this);
        toggleableListeners.clear();
        syncCommandsToPlayers();
    }

    /**
     * Re-reads config.yml and enables or disables features accordingly.
     */
    public void reloadSettings() {
        config.load();
        applySettings();
    }

    private void applySettings() {
        PluginManager pm = getServer().getPluginManager();
        for (ToggleableListener toggleable : toggleableListeners) {
            HandlerList.unregisterAll(toggleable.listener());
            if (toggleable.enabled().test(config)) {
                pm.registerEvents(toggleable.listener(), this);
            }
        }

        // In "default" mode WorldGuard's own PvP handling is used untouched.
        if (config.miscDefaultPvPFlagOperationMode != null) {
            tryInject("PvP mode", () -> pvpListener.inject(this));
        } else {
            tryRun("WorldGuard PvP listener", () -> pvpListener.uninject());
        }

        boolean commandsChanged;
        if (config.extendedWorldEditWandEnabled) {
            commandsChanged = tryInject("//wand", () -> wandCommandWrapper.inject());
        } else {
            commandsChanged = tryRun("//wand wrapper", () -> wandCommandWrapper.uninject());
        }
        if (commandsChanged) {
            syncCommandsToPlayers();
        }
    }

    private static void syncCommandsToPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    private boolean tryInject(String feature, CheckedRunnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Unable to enable " + feature + ", the feature will stay disabled", t);
            return false;
        }
    }

    private boolean tryRun(String feature, CheckedRunnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Unable to disable " + feature, t);
            return false;
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private record ToggleableListener(Listener listener, Predicate<Config> enabled) {
    }
}
