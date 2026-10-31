package me.x1no4ekk.core;

import me.x1no4ekk.core.command.CoreCommand;
import me.x1no4ekk.core.command.DelHomeCommand;
import me.x1no4ekk.core.command.HomeCommand;
import me.x1no4ekk.core.command.PlayerHomeCommand;
import me.x1no4ekk.core.command.SetHomeCommand;
import me.x1no4ekk.core.config.ConfigFile;
import me.x1no4ekk.core.config.Messages;
import me.x1no4ekk.core.config.Settings;
import me.x1no4ekk.core.database.DatabaseManager;
import me.x1no4ekk.core.gui.GuiListener;
import me.x1no4ekk.core.home.HomeManager;
import me.x1no4ekk.core.home.HomeRepository;
import me.x1no4ekk.core.home.HomeTeleporter;
import me.x1no4ekk.core.horn.HornListener;
import me.x1no4ekk.core.horn.TeleportHorn;
import me.x1no4ekk.core.user.UserListener;
import me.x1no4ekk.core.user.UserManager;
import me.x1no4ekk.core.user.UserRepository;
import me.x1no4ekk.core.util.MainThreadExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;
import java.util.logging.Level;

public final class CorePlugin extends JavaPlugin {

    private volatile Settings settings;
    private Messages messages;
    private Executor mainThread;
    private DatabaseManager database;
    private UserManager userManager;
    private HomeManager homeManager;
    private HomeTeleporter homeTeleporter;
    private TeleportHorn teleportHorn;

    @Override
    public void onEnable() {
        this.mainThread = new MainThreadExecutor(this);
        this.messages = new Messages(getLogger());

        try {
            loadConfiguration();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Invalid configuration (settings.yml / messages.yml): " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Hard requirement: without MySQL the plugin must not run at all.
        DatabaseManager db = new DatabaseManager(getLogger(), settings.database());
        try {
            db.connect();
            db.createSchema();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "==============================================================");
            getLogger().log(Level.SEVERE, " Core: cannot connect to MySQL at " + settings.database().host() + ":"
                    + settings.database().port() + "/" + settings.database().name());
            getLogger().log(Level.SEVERE, " Reason: " + e.getMessage());
            getLogger().log(Level.SEVERE, " Check the 'database' section in settings.yml. The plugin is now DISABLED.");
            getLogger().log(Level.SEVERE, "==============================================================", e);
            db.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.database = db;

        HomeRepository homeRepository = new HomeRepository();
        this.userManager = new UserManager(this, database, new UserRepository(homeRepository), mainThread);
        this.homeManager = new HomeManager(getLogger(), database, homeRepository, this::settings, mainThread);
        this.teleportHorn = new TeleportHorn(this, this::settings);
        this.homeTeleporter = new HomeTeleporter(userManager, homeManager, teleportHorn, messages, mainThread);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new UserListener(userManager, messages, getLogger(),
                () -> settings.users().loadTimeoutSeconds()), this);
        pluginManager.registerEvents(new GuiListener(), this);
        pluginManager.registerEvents(new HornListener(this, teleportHorn), this);

        registerCommand("core", new CoreCommand(this));
        registerCommand("sethome", new SetHomeCommand(this));
        registerCommand("delhome", new DelHomeCommand(this));
        registerCommand("playerhome", new PlayerHomeCommand(this));
        registerCommand("home", new HomeCommand(this));

        teleportHorn.registerRecipe();
        userManager.start(settings.users().autosaveIntervalMinutes());

        // Players already online (plugin enabled late / reloaded) missed AsyncPlayerPreLoginEvent.
        for (Player player : getServer().getOnlinePlayers()) {
            userManager.loadAsync(player.getUniqueId(), player.getName());
            teleportHorn.discoverRecipe(player);
        }

        getLogger().info("Core enabled: MySQL connected, " + getServer().getOnlinePlayers().size() + " online player(s) loading.");
    }

    @Override
    public void onDisable() {
        GuiListener.closeAll();
        if (teleportHorn != null) {
            teleportHorn.unregisterRecipe();
        }
        if (userManager != null) {
            userManager.shutdown();
        }
        if (database != null) {
            database.close(); // drains queued writes, including the final saves above
        }
        userManager = null;
        homeManager = null;
        homeTeleporter = null;
        database = null;
    }

    private void loadConfiguration() throws Exception {
        YamlConfiguration settingsFile = ConfigFile.load(this, "settings.yml");
        YamlConfiguration messagesFile = ConfigFile.load(this, "messages.yml");
        Settings loaded = Settings.load(settingsFile);
        messages.load(messagesFile);
        this.settings = loaded;
    }

    /**
     * Reloads settings.yml and messages.yml. Database settings require a restart.
     *
     * @throws Exception if a file is invalid; the previous settings remain active
     */
    public void reloadSettings() throws Exception {
        YamlConfiguration settingsFile = ConfigFile.load(this, "settings.yml");
        YamlConfiguration messagesFile = ConfigFile.load(this, "messages.yml");
        Settings loaded = Settings.load(settingsFile);
        messages.load(messagesFile);
        this.settings = loaded;
        teleportHorn.registerRecipe();
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    // ---------------------------------------------------------------- accessors

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Executor mainThread() {
        return mainThread;
    }

    public DatabaseManager database() {
        return database;
    }

    public UserManager userManager() {
        return userManager;
    }

    public HomeManager homeManager() {
        return homeManager;
    }

    public HomeTeleporter homeTeleporter() {
        return homeTeleporter;
    }

    public TeleportHorn teleportHorn() {
        return teleportHorn;
    }
}
