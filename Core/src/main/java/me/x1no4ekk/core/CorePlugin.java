package me.x1no4ekk.core;

import me.x1no4ekk.core.api.CoreApi;
import me.x1no4ekk.core.api.CoreApiImpl;
import me.x1no4ekk.core.combat.CombatManager;
import me.x1no4ekk.core.command.AdminCommand;
import me.x1no4ekk.core.command.CommandRegistry;
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
import me.x1no4ekk.core.hook.PlaceholderHook;
import me.x1no4ekk.core.hook.VaultHook;
import me.x1no4ekk.core.horn.HornListener;
import me.x1no4ekk.core.horn.TeleportHorn;
import me.x1no4ekk.core.module.CoreModule;
import me.x1no4ekk.core.session.SessionManager;
import me.x1no4ekk.core.teleport.TeleportService;
import me.x1no4ekk.core.user.UserListener;
import me.x1no4ekk.core.user.UserManager;
import me.x1no4ekk.core.user.UserRepository;
import me.x1no4ekk.core.util.MainThreadExecutor;
import me.x1no4ekk.core.vanish.VanishManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;

public final class CorePlugin extends JavaPlugin {

    private volatile Settings settings;
    private Messages messages;
    private Executor mainThread;
    private DatabaseManager database;
    private UserManager userManager;
    private SessionManager sessions;
    private CommandRegistry commands;
    private VaultHook vault;
    private PlaceholderHook placeholders;
    private VanishManager vanishManager;
    private CombatManager combatManager;
    private TeleportService teleportService;
    private HomeManager homeManager;
    private HomeTeleporter homeTeleporter;
    private TeleportHorn teleportHorn;
    private CoreApiImpl api;
    private final List<CoreModule> modules = new ArrayList<>();

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

        // Shared services
        HomeRepository homeRepository = new HomeRepository();
        this.userManager = new UserManager(this, database, new UserRepository(homeRepository), mainThread);
        this.sessions = new SessionManager(getLogger(), userManager);
        this.commands = new CommandRegistry(this);
        this.vault = new VaultHook(this);
        this.placeholders = new PlaceholderHook();
        this.vanishManager = new VanishManager(this);
        this.combatManager = new CombatManager(this);
        this.teleportService = new TeleportService(this);
        this.homeManager = new HomeManager(getLogger(), database, homeRepository, this::settings, mainThread);
        this.teleportHorn = new TeleportHorn(this, this::settings);
        this.homeTeleporter = new HomeTeleporter(userManager, teleportService, combatManager, teleportHorn, messages, mainThread);

        registerListener(new UserListener(userManager, messages, getLogger(), () -> settings.users().loadTimeoutSeconds()));
        registerListener(sessions);
        registerListener(new GuiListener());
        registerListener(vanishManager);
        registerListener(combatManager);
        registerListener(teleportService);
        registerListener(new HornListener(this, teleportHorn));

        commands.register(new AdminCommand(this));
        commands.register(new SetHomeCommand(this));
        commands.register(new DelHomeCommand(this));
        commands.register(new PlayerHomeCommand(this));
        commands.register(new HomeCommand(this));

        for (CoreModule module : createModules()) {
            try {
                module.enable();
                modules.add(module);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Module '" + module.name() + "' failed to start and is disabled: " + e.getMessage(), e);
            }
        }

        commands.install();
        teleportHorn.registerRecipe();
        userManager.start(settings.users().autosaveIntervalMinutes());

        this.api = new CoreApiImpl(this);
        getServer().getServicesManager().register(CoreApi.class, api, this, ServicePriority.Normal);

        // Players already online (plugin enabled late) missed AsyncPlayerPreLoginEvent.
        for (Player player : getServer().getOnlinePlayers()) {
            userManager.loadAsync(player.getUniqueId(), player.getName());
            teleportHorn.discoverRecipe(player);
        }

        getLogger().info("Core enabled: MySQL connected, " + modules.size() + " module(s), "
                + commands.commands().size() + " command(s).");
    }

    /**
     * Feature modules in start order.
     */
    private List<CoreModule> createModules() {
        return List.of();
    }

    @Override
    public void onDisable() {
        GuiListener.closeAll();
        for (int i = modules.size() - 1; i >= 0; i--) {
            CoreModule module = modules.get(i);
            try {
                module.disable();
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "Module '" + module.name() + "' failed to stop cleanly", e);
            }
        }
        modules.clear();
        getServer().getServicesManager().unregisterAll(this);
        if (teleportService != null) {
            teleportService.shutdown();
        }
        if (combatManager != null) {
            combatManager.shutdown();
        }
        if (vanishManager != null) {
            vanishManager.shutdown();
        }
        if (teleportHorn != null) {
            teleportHorn.unregisterRecipe();
        }
        if (userManager != null) {
            userManager.shutdown();
        }
        if (database != null) {
            database.close(); // drains queued writes, including the final saves above
        }
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        api = null;
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
     * /core reload: settings.yml, messages.yml and every module's own file.
     * Database settings and disabled-commands require a restart.
     *
     * @throws Exception if a file is invalid; previously loaded values remain active for that file
     */
    public void reloadAll() throws Exception {
        loadConfiguration();
        teleportHorn.registerRecipe();
        Exception failure = null;
        for (CoreModule module : modules) {
            try {
                module.reload();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Module '" + module.name() + "' failed to reload", e);
                if (failure == null) {
                    failure = new Exception(module.name() + ": " + e.getMessage(), e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    // ---------------------------------------------------------------- accessors

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    /**
     * Executor for CompletableFuture continuations that must run on the server thread.
     */
    public Executor mainThread() {
        return mainThread;
    }

    public DatabaseManager database() {
        return database;
    }

    public UserManager userManager() {
        return userManager;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public VaultHook vault() {
        return vault;
    }

    public PlaceholderHook placeholders() {
        return placeholders;
    }

    public VanishManager vanishManager() {
        return vanishManager;
    }

    public CombatManager combatManager() {
        return combatManager;
    }

    public TeleportService teleportService() {
        return teleportService;
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

    public CoreApi api() {
        return api;
    }
}
