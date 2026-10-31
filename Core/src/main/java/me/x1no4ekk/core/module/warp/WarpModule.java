package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.config.ConfigFile;
import me.x1no4ekk.core.module.CoreModule;

import java.sql.Statement;

/**
 * Warps (player and server), warp menus and grades, /baltop /killtop /deathtop, kill/death statistics
 * and KillerMoney rewards.
 */
public final class WarpModule implements CoreModule {

    private static final String WARPS_FILE = "warps.yml";
    private static final String KILLER_MONEY_FILE = "killermoney.yml";

    private final CorePlugin plugin;
    private volatile WarpConfig config;
    private volatile KillerMoneyConfig killerMoneyConfig;
    private WarpManager manager;
    private WarpTeleporter teleporter;
    private TopCache tops;
    private KillerMoneyListener killerMoney;

    public WarpModule(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "warp";
    }

    @Override
    public void enable() throws Exception {
        this.config = WarpConfig.load(ConfigFile.load(plugin, WARPS_FILE));
        this.killerMoneyConfig = KillerMoneyConfig.load(ConfigFile.load(plugin, KILLER_MONEY_FILE));

        plugin.database().execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(WarpRepository.CREATE_GRADES_TABLE);
            }
            return null;
        });

        this.manager = new WarpManager(plugin, new WarpRepository(plugin.getLogger()), this::config);
        manager.loadAll();
        this.teleporter = new WarpTeleporter(plugin, manager);
        this.tops = new TopCache(plugin, this::config);
        this.killerMoney = new KillerMoneyListener(plugin, this::killerMoneyConfig);

        if (!plugin.vault().hasEconomy()) {
            plugin.getLogger().info("KillerMoney: Vault economy not found, rewards stay inactive until an economy is registered.");
        }

        plugin.registerListener(new StatsListener(plugin.userManager()));
        plugin.registerListener(killerMoney);

        plugin.commands().register(new SetWarpCommand(this));
        plugin.commands().register(new DelWarpCommand(this));
        plugin.commands().register(new WarpCommand(this));
        plugin.commands().register(new WarpInfoCommand(this));
        plugin.commands().register(new ServerWarpCommand(this));
        plugin.commands().register(new TopCommand(this, TopCache.Type.BALANCE, "baltop", "core.baltop", "Топ игроков по балансу", "balancetop"));
        plugin.commands().register(new TopCommand(this, TopCache.Type.KILLS, "killtop", "core.killtop", "Топ игроков по убийствам"));
        plugin.commands().register(new TopCommand(this, TopCache.Type.DEATHS, "deathtop", "core.deathtop", "Топ игроков по смертям"));

        plugin.sessions().onQuit(player -> killerMoney.clear(player.getUniqueId()));

        tops.start();
        killerMoney.start();
        plugin.getLogger().info("Warps: loaded " + manager.names().size() + " warp(s).");
    }

    @Override
    public void reload() throws Exception {
        WarpConfig warps = WarpConfig.load(ConfigFile.load(plugin, WARPS_FILE));
        KillerMoneyConfig rewards = KillerMoneyConfig.load(ConfigFile.load(plugin, KILLER_MONEY_FILE));
        this.config = warps;
        this.killerMoneyConfig = rewards;
        tops.reschedule();
    }

    @Override
    public void disable() {
        if (tops != null) {
            tops.stop();
        }
        if (killerMoney != null) {
            killerMoney.stop();
        }
    }

    // ---------------------------------------------------------------- accessors for commands and menus

    CorePlugin plugin() {
        return plugin;
    }

    WarpConfig config() {
        return config;
    }

    KillerMoneyConfig killerMoneyConfig() {
        return killerMoneyConfig;
    }

    WarpManager manager() {
        return manager;
    }

    WarpTeleporter teleporter() {
        return teleporter;
    }

    TopCache tops() {
        return tops;
    }
}
