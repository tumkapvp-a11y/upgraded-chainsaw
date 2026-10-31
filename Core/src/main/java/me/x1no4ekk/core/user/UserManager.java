package me.x1no4ekk.core.user;

import me.x1no4ekk.core.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cache of online users only.
 * <ul>
 *     <li>loaded on {@code AsyncPlayerPreLoginEvent} (blocking is allowed there);</li>
 *     <li>removed from RAM and saved asynchronously on quit;</li>
 *     <li>entries for logins that never reached the server are evicted by a cleanup task.</li>
 * </ul>
 */
public final class UserManager {

    /** Grace period between pre-login and join before an orphaned entry is evicted. */
    private static final long ORPHAN_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);

    private final Plugin plugin;
    private final Logger logger;
    private final DatabaseManager database;
    private final UserRepository repository;
    private final Executor mainThread;

    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    /** In-flight saves; a re-joining player waits for its own save before loading. Entries remove themselves. */
    private final Map<UUID, CompletableFuture<Void>> pendingSaves = new ConcurrentHashMap<>();

    private BukkitTask autosaveTask;
    private BukkitTask cleanupTask;

    public UserManager(Plugin plugin, DatabaseManager database, UserRepository repository, Executor mainThread) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.database = database;
        this.repository = repository;
        this.mainThread = mainThread;
    }

    public void start(int autosaveIntervalMinutes) {
        long autosaveTicks = TimeUnit.MINUTES.toSeconds(autosaveIntervalMinutes) * 20L;
        this.autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, autosaveTicks, autosaveTicks);
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::evictOrphans, 20L * 30, 20L * 30);
    }

    // ---------------------------------------------------------------- lookup

    public User getUser(UUID uuid) {
        return users.get(uuid);
    }

    public User getUser(Player player) {
        return users.get(player.getUniqueId());
    }

    public boolean isLoaded(UUID uuid) {
        return users.containsKey(uuid);
    }

    public Collection<User> getUsers() {
        return Collections.unmodifiableCollection(users.values());
    }

    /**
     * Case-insensitive name lookup in MySQL (for offline players).
     */
    public CompletableFuture<Optional<Map.Entry<UUID, String>>> findByName(String name) {
        return database.query(connection -> repository.findByName(connection, name));
    }

    // ---------------------------------------------------------------- loading

    /**
     * Blocking load used by the pre-login thread. Waits for a pending save of the same player first,
     * so a fast re-join never reads stale data.
     */
    User loadBlocking(UUID uuid, String name, long timeoutSeconds) throws Exception {
        awaitPendingSave(uuid, timeoutSeconds);
        User user = database.query(connection -> repository.loadOrCreate(connection, uuid, name))
                .get(timeoutSeconds, TimeUnit.SECONDS);
        users.put(uuid, user);
        return user;
    }

    /**
     * Fallback load for players already online (plugin enabled late or a duplicate-login race).
     * The user is cached only if the player is still online when loading completes.
     */
    public CompletableFuture<User> loadAsync(UUID uuid, String name) {
        CompletableFuture<Void> pending = pendingSaves.get(uuid);
        CompletableFuture<Void> barrier = pending == null
                ? CompletableFuture.completedFuture(null)
                : pending.exceptionally(error -> null);
        return barrier
                .thenCompose(ignored -> database.query(connection -> repository.loadOrCreate(connection, uuid, name)))
                .thenApplyAsync(user -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        return null;
                    }
                    User existing = users.putIfAbsent(uuid, user);
                    return existing != null ? existing : user;
                }, mainThread)
                .whenComplete((user, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to load data of " + name + " (" + uuid + ")", error);
                    }
                });
    }

    private void awaitPendingSave(UUID uuid, long timeoutSeconds) throws Exception {
        CompletableFuture<Void> pending = pendingSaves.get(uuid);
        if (pending != null) {
            try {
                pending.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException ignored) {
                // The failed save was already logged; load whatever is stored.
            }
        }
    }

    /**
     * Drops an entry whose login was denied after it had been loaded.
     */
    void discardIfOffline(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null) {
            users.remove(uuid);
        }
    }

    // ---------------------------------------------------------------- persistent cooldowns

    /**
     * @return milliseconds left on a persistent cooldown (survives relogs and restarts), 0 if none
     */
    public long getCooldownRemaining(User user, String key) {
        long expiry = user.getCooldownExpiry(key);
        return expiry == 0 ? 0 : Math.max(0, expiry - System.currentTimeMillis());
    }

    /**
     * Starts a persistent cooldown: cached in the user and written to core_cooldowns asynchronously.
     * Use for long cooldowns (minutes+); short ones belong in an in-memory CooldownTracker.
     */
    public void startCooldown(User user, String key, long durationMillis) {
        if (durationMillis <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + durationMillis;
        user.putCooldown(key, expiresAt);
        UUID uuid = user.getUuid();
        database.update(connection -> repository.saveCooldown(connection, uuid, key, expiresAt))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to save cooldown '" + key + "' of " + uuid, error);
                    }
                });
    }

    // ---------------------------------------------------------------- unloading

    /**
     * Removes the user from RAM and saves the final snapshot asynchronously.
     */
    public void unload(UUID uuid) {
        User user = users.remove(uuid);
        if (user != null) {
            save(user);
        }
    }

    /**
     * Saves a single user on the serial writer and tracks the save until it finishes.
     */
    public CompletableFuture<Void> save(User user) {
        UserSnapshot snapshot = user.snapshot();
        user.clearDirty();
        UUID uuid = user.getUuid();
        CompletableFuture<Void> future = database.update(connection -> repository.save(connection, snapshot));
        pendingSaves.put(uuid, future);
        future.whenComplete((ignored, error) -> {
            pendingSaves.remove(uuid, future);
            if (error != null) {
                logger.log(Level.SEVERE, "Failed to save data of " + snapshot.name() + " (" + uuid + ")", error);
                mainThread.execute(() -> {
                    User current = users.get(uuid);
                    if (current != null) {
                        current.markDirty(); // retry with the next autosave
                    }
                });
            }
        });
        return future;
    }

    private void autosave() {
        List<UserSnapshot> batch = new ArrayList<>();
        for (User user : users.values()) {
            if (user.isDirty()) {
                batch.add(user.snapshot());
                user.clearDirty();
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        database.updateInTransaction(connection -> repository.saveAll(connection, batch))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Autosave of " + batch.size() + " users failed", error);
                        mainThread.execute(() -> batch.forEach(snapshot -> {
                            User current = users.get(snapshot.uuid());
                            if (current != null) {
                                current.markDirty();
                            }
                        }));
                    }
                });
    }

    /**
     * Evicts entries loaded on pre-login whose player never joined (denied login, dropped connection).
     */
    private void evictOrphans() {
        long now = System.currentTimeMillis();
        users.forEach((uuid, user) -> {
            if (now - user.getLoadedAt() > ORPHAN_TIMEOUT_MILLIS && Bukkit.getPlayer(uuid) == null) {
                if (users.remove(uuid, user) && user.isDirty()) {
                    save(user);
                }
            }
        });
    }

    /**
     * Queues the final save of every cached user and clears the cache. The queued writes are drained by
     * {@link DatabaseManager#close()}, which runs right after this.
     */
    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (!database.isRunning()) {
            users.clear();
            return;
        }
        List<UserSnapshot> all = new ArrayList<>(users.size());
        for (User user : users.values()) {
            all.add(user.snapshot());
        }
        users.clear();
        if (!all.isEmpty()) {
            database.updateInTransaction(connection -> repository.saveAll(connection, all))
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            logger.log(Level.SEVERE, "Final save of " + all.size() + " users failed", error);
                        }
                    });
        }
    }
}
