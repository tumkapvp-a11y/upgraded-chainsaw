package dev.applesub;

import dev.applesub.command.CommandRegistrar;
import dev.applesub.config.Settings;
import dev.applesub.data.Feature;
import dev.applesub.data.PlayerData;
import dev.applesub.data.PlayerDataManager;
import dev.applesub.feature.AntiFireInjector;
import dev.applesub.feature.AntiTrashListener;
import dev.applesub.feature.ArmorNotifyListener;
import dev.applesub.feature.FeatureTask;
import dev.applesub.feature.NightVision;
import dev.applesub.gui.Menu;
import dev.applesub.gui.MenuListener;
import dev.applesub.listener.ConnectionListener;
import dev.applesub.service.SubscriptionService;
import dev.applesub.storage.Database;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

public final class AppleSubPlugin extends JavaPlugin {

    private Settings settings;
    private Database database;
    private PlayerDataManager dataManager;
    private SubscriptionService service;
    private AntiFireInjector antiFire;
    private BukkitTask featureTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();

        database = new Database(getLogger());
        try {
            database.connect(getConfig().getConfigurationSection("storage"), getDataFolder());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось подключиться к базе данных, плагин отключён", e);
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dataManager = new PlayerDataManager(this);
        service = new SubscriptionService(this);
        antiFire = AntiFireInjector.create(this);

        ConnectionListener connectionListener = new ConnectionListener(this);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(connectionListener, this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new AntiTrashListener(this), this);
        pm.registerEvents(new ArmorNotifyListener(this), this);

        CommandRegistrar.register(this);
        featureTask = new FeatureTask(this).runTaskTimer(this, 20L, 20L);

        // Covers /reload and plugin managers: players already online need their data.
        for (Player player : getServer().getOnlinePlayers()) {
            connectionListener.handleJoin(player);
        }
        getLogger().info("AppleSub включён (хранилище: " + database.type() + ")");
    }

    @Override
    public void onDisable() {
        if (featureTask != null) {
            featureTask.cancel();
        }
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                player.closeInventory();
            }
            if (antiFire != null) {
                antiFire.eject(player);
            }
            // Don't let the infinite effect get saved into player data while the plugin is gone.
            PlayerData data = dataManager == null ? null : dataManager.get(player.getUniqueId());
            if (data != null && data.isActive(Feature.NIGHT_VISION)) {
                NightVision.remove(player);
            }
        }
        if (database != null) {
            database.close();
        }
    }

    /** Runs the task on the main thread if the plugin is still enabled. */
    public void sync(Runnable task) {
        if (isEnabled()) {
            getServer().getScheduler().runTask(this, task);
        }
    }

    public Settings settings() {
        return settings;
    }

    public Database database() {
        return database;
    }

    public PlayerDataManager dataManager() {
        return dataManager;
    }

    public SubscriptionService service() {
        return service;
    }

    public AntiFireInjector antiFire() {
        return antiFire;
    }
}
