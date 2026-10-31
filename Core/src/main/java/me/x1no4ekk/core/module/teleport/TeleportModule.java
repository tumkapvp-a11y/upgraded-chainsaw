package me.x1no4ekk.core.module.teleport;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.config.ConfigFile;
import me.x1no4ekk.core.module.CoreModule;
import me.x1no4ekk.core.module.teleport.rtp.RtpCommand;
import me.x1no4ekk.core.module.teleport.rtp.RtpService;
import me.x1no4ekk.core.module.teleport.spawn.SetSpawnCommand;
import me.x1no4ekk.core.module.teleport.spawn.SpawnCommand;
import me.x1no4ekk.core.module.teleport.spawn.SpawnListener;
import me.x1no4ekk.core.module.teleport.spawn.SpawnManager;
import me.x1no4ekk.core.module.teleport.tp.TpAllCommand;
import me.x1no4ekk.core.module.teleport.tp.TpCommand;
import me.x1no4ekk.core.module.teleport.tp.TpHereCommand;
import me.x1no4ekk.core.module.teleport.tp.TpPosCommand;
import me.x1no4ekk.core.module.teleport.tpa.TpAcceptCommand;
import me.x1no4ekk.core.module.teleport.tpa.TpCancelCommand;
import me.x1no4ekk.core.module.teleport.tpa.TpDenyCommand;
import me.x1no4ekk.core.module.teleport.tpa.TpToggleCommand;
import me.x1no4ekk.core.module.teleport.tpa.TpaCommand;
import me.x1no4ekk.core.module.teleport.tpa.TpaManager;

/**
 * Teleport module: /tp, /tphere, /tppos, /tpall, the /tpa request system, /back, /top, /spawn and /rtp.
 * Every teleport goes through {@link me.x1no4ekk.core.teleport.TeleportService}.
 */
public final class TeleportModule implements CoreModule {

    private static final String CONFIG_FILE = "teleports.yml";

    private final CorePlugin plugin;
    private volatile TeleportConfig config;
    private TpaManager tpaManager;
    private RtpService rtpService;

    public TeleportModule(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "teleport";
    }

    @Override
    public void enable() throws Exception {
        this.config = TeleportConfig.parse(ConfigFile.load(plugin, CONFIG_FILE));

        SpawnManager spawnManager = new SpawnManager(plugin);
        spawnManager.createTableAndLoad();

        this.tpaManager = new TpaManager(plugin, this::config);
        this.rtpService = new RtpService(plugin, this::config);
        plugin.sessions().onQuit(tpaManager::handleQuit);
        plugin.sessions().onQuit(rtpService::handleQuit);
        plugin.registerListener(new SpawnListener(plugin, spawnManager, this::config));

        TpPosCommand tpPos = new TpPosCommand(plugin);
        plugin.commands().register(new TpCommand(plugin, tpPos));
        plugin.commands().register(new TpHereCommand(plugin));
        plugin.commands().register(tpPos);
        plugin.commands().register(new TpAllCommand(plugin));

        plugin.commands().register(new TpaCommand(plugin, tpaManager, this::config));
        plugin.commands().register(new TpAcceptCommand(plugin, tpaManager));
        plugin.commands().register(new TpDenyCommand(plugin, tpaManager));
        plugin.commands().register(new TpCancelCommand(plugin, tpaManager));
        plugin.commands().register(new TpToggleCommand(plugin));

        plugin.commands().register(new BackCommand(plugin));
        plugin.commands().register(new TopCommand(plugin));
        plugin.commands().register(new SetSpawnCommand(plugin, spawnManager));
        plugin.commands().register(new SpawnCommand(plugin, spawnManager));
        plugin.commands().register(new RtpCommand(plugin, rtpService, this::config));
    }

    @Override
    public void reload() throws Exception {
        this.config = TeleportConfig.parse(ConfigFile.load(plugin, CONFIG_FILE));
    }

    @Override
    public void disable() {
        if (tpaManager != null) {
            tpaManager.shutdown();
        }
        if (rtpService != null) {
            rtpService.shutdown();
        }
    }

    public TeleportConfig config() {
        return config;
    }
}
