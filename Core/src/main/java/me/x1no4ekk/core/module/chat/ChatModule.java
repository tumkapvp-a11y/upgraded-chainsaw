package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.CommandRegistry;
import me.x1no4ekk.core.config.ConfigFile;
import me.x1no4ekk.core.module.CoreModule;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.CooldownTracker;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat: global/local chat with privileges, donate/staff channels, private messages with spying, ignores,
 * paid broadcasts and auto-broadcasts. Configured in chat.yml.
 */
public final class ChatModule implements CoreModule {

    /** Position/Vault sampling period for the async chat thread. */
    private static final long SAMPLE_PERIOD_TICKS = 10L;

    private final CorePlugin plugin;
    private final CooldownTracker chatCooldown = new CooldownTracker();
    private final CooldownTracker donateCooldown = new CooldownTracker();
    private final CooldownTracker staffCooldown = new CooldownTracker();
    /** Join timestamps for the chat delay after joining; written on the main thread, read by the chat thread. */
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private final PlayerStates states;
    private final ChatFormatter formatter;
    private final PrivateMessageService privateMessages;
    private final AutoBroadcaster autoBroadcaster;

    private volatile ChatConfig config;
    private volatile boolean muted;
    private BukkitTask sampleTask;

    public ChatModule(CorePlugin plugin) {
        this.plugin = plugin;
        this.states = new PlayerStates(plugin.vault());
        this.formatter = new ChatFormatter(plugin);
        this.privateMessages = new PrivateMessageService(plugin, this);
        this.autoBroadcaster = new AutoBroadcaster(plugin, this);
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public void enable() throws Exception {
        this.config = loadConfig();

        plugin.registerListener(new ChatListener(plugin, this));
        plugin.registerListener(privateMessages);

        CommandRegistry commands = plugin.commands();
        commands.register(new ChatAdminCommand(plugin, this));
        commands.register(new ToggleCommand(plugin, "chattoggle", ChatPermissions.CHAT_TOGGLE, "Включить или выключить чат",
                User::isChatToggle, User::setChatToggle, "chat.toggle-usage", "chat.toggle-on", "chat.toggle-off"));
        commands.register(new ChannelCommand(plugin, this, ChannelCommand.Kind.DONATE));
        commands.register(new ChannelCommand(plugin, this, ChannelCommand.Kind.STAFF));
        commands.register(new MsgCommand(plugin, privateMessages));
        commands.register(new ReplyCommand(plugin, privateMessages));
        commands.register(new ToggleCommand(plugin, "msgtoggle", ChatPermissions.MSG_TOGGLE, "Включить или выключить личные сообщения",
                User::isMsgToggle, User::setMsgToggle, "msg.toggle-usage", "msg.toggle-on", "msg.toggle-off"));
        commands.register(new ToggleCommand(plugin, "socialspy", ChatPermissions.SOCIAL_SPY, "Слежка за личными сообщениями",
                User::isSocialSpy, User::setSocialSpy, "spy.social-usage", "spy.social-on", "spy.social-off"));
        commands.register(new TargetSpyCommand(plugin, privateMessages));
        commands.register(new IgnoreCommand(plugin));
        // Brigadier permissions are fixed at registration; the live chat.yml value is checked again on execution.
        commands.register(new BroadcastCommand(plugin, this, "broadcast", "broadcast.broadcast-usage", false, "bc"));
        commands.register(new BroadcastCommand(plugin, this, "ad", "broadcast.ad-usage", false));
        commands.register(new BroadcastCommand(plugin, this, "buy", "broadcast.buy-usage", false));
        commands.register(new BroadcastCommand(plugin, this, "sell", "broadcast.sell-usage", false));
        commands.register(new BroadcastCommand(plugin, this, "stream", "broadcast.stream-usage", true));
        commands.register(new BroadcastCommand(plugin, this, "video", "broadcast.video-usage", true));

        plugin.sessions().onQuit(this::handleQuit);

        // Players already online (late enable) have no join timestamp and therefore no join delay.
        states.refreshAll();
        sampleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, SAMPLE_PERIOD_TICKS, SAMPLE_PERIOD_TICKS);
        autoBroadcaster.restart(config.autoBroadcast());
    }

    @Override
    public void reload() throws Exception {
        ChatConfig loaded = loadConfig();
        this.config = loaded;
        autoBroadcaster.restart(loaded.autoBroadcast());
    }

    @Override
    public void disable() {
        if (sampleTask != null) {
            sampleTask.cancel();
            sampleTask = null;
        }
        autoBroadcaster.stop();
        privateMessages.clear();
        chatCooldown.clearAll();
        donateCooldown.clearAll();
        staffCooldown.clearAll();
        joinTimes.clear();
        states.clear();
    }

    private ChatConfig loadConfig() throws Exception {
        return ChatConfig.parse(ConfigFile.load(plugin, name() + ".yml"));
    }

    private void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        chatCooldown.clear(uuid);
        donateCooldown.clear(uuid);
        staffCooldown.clear(uuid);
        joinTimes.remove(uuid);
        states.remove(uuid);
        privateMessages.handleQuit(player);
    }

    /**
     * Main-thread sampler: refreshes snapshots and expires join timestamps.
     */
    private void sample() {
        states.refreshAll();
        long now = System.currentTimeMillis();
        long delay = config.joinDelayMillis();
        joinTimes.entrySet().removeIf(entry -> now - entry.getValue() >= delay || Bukkit.getPlayer(entry.getKey()) == null);
    }

    // ---------------------------------------------------------------- shared state

    ChatConfig config() {
        return config;
    }

    boolean isMuted() {
        return muted;
    }

    void setMuted(boolean muted) {
        this.muted = muted;
    }

    PlayerStates states() {
        return states;
    }

    ChatFormatter formatter() {
        return formatter;
    }

    CooldownTracker chatCooldown() {
        return chatCooldown;
    }

    CooldownTracker donateCooldown() {
        return donateCooldown;
    }

    CooldownTracker staffCooldown() {
        return staffCooldown;
    }

    void recordJoin(UUID uuid) {
        joinTimes.put(uuid, System.currentTimeMillis());
    }

    /**
     * Thread-safe.
     *
     * @return milliseconds until the player may chat after joining, 0 if allowed
     */
    long joinDelayRemaining(UUID uuid, long delayMillis) {
        Long joinedAt = joinTimes.get(uuid);
        if (joinedAt == null) {
            return 0;
        }
        return Math.max(0, joinedAt + delayMillis - System.currentTimeMillis());
    }

    /**
     * Sends a message on the main thread to the player if still online (safe to call from any thread).
     */
    void notify(UUID uuid, String key, TagResolver... resolvers) {
        plugin.mainThread().execute(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, key, resolvers);
            }
        });
    }
}
