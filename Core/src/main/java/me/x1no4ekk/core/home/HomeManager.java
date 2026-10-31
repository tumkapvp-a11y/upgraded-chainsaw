package me.x1no4ekk.core.home;

import me.x1no4ekk.core.config.Settings;
import me.x1no4ekk.core.database.DatabaseManager;
import me.x1no4ekk.core.user.User;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Home points: the cache lives in {@link User}, MySQL is updated asynchronously on the serial writer.
 * The cache is updated optimistically and rolled back if the write fails.
 */
public final class HomeManager {

    public enum CreateResult {
        CREATED, INVALID_NAME, ALREADY_EXISTS, LIMIT_REACHED, DISABLED_WORLD, SPECTATOR
    }

    private final Logger logger;
    private final DatabaseManager database;
    private final HomeRepository repository;
    private final Supplier<Settings> settings;
    private final Executor mainThread;

    public HomeManager(Logger logger, DatabaseManager database, HomeRepository repository,
                       Supplier<Settings> settings, Executor mainThread) {
        this.logger = logger;
        this.database = database;
        this.repository = repository;
        this.settings = settings;
        this.mainThread = mainThread;
    }

    /**
     * Highest limit among {@code core.homes.<group>} permissions; {@code core.homes.unlimited} removes the cap.
     */
    public int getHomeLimit(Player player) {
        if (player.hasPermission("core.homes.unlimited")) {
            return Integer.MAX_VALUE;
        }
        Settings.HomeSettings config = settings.get().homes();
        int limit = config.defaultLimit();
        for (Map.Entry<String, Integer> entry : config.limits().entrySet()) {
            if (entry.getValue() > limit && player.hasPermission("core.homes." + entry.getKey())) {
                limit = entry.getValue();
            }
        }
        return limit;
    }

    public boolean isValidName(String name) {
        return settings.get().homes().namePattern().matcher(name).matches();
    }

    /**
     * Validates and creates a home at the player's position. Must be called on the main thread.
     *
     * @param onFailure invoked on the main thread if the database write fails (cache already rolled back)
     */
    public CreateResult createHome(Player player, User user, String name, Runnable onFailure) {
        if (!isValidName(name)) {
            return CreateResult.INVALID_NAME;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return CreateResult.SPECTATOR;
        }
        Location location = player.getLocation();
        if (settings.get().homes().disabledWorlds().contains(location.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return CreateResult.DISABLED_WORLD;
        }
        if (user.hasHome(name)) {
            return CreateResult.ALREADY_EXISTS;
        }
        if (user.getHomeCount() >= getHomeLimit(player)) {
            return CreateResult.LIMIT_REACHED;
        }

        Home home = Home.of(name, location);
        user.putHome(home);
        UUID owner = user.getUuid();
        database.update(connection -> repository.upsert(connection, owner, home))
                .whenCompleteAsync((ignored, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to save home '" + name + "' of " + owner, error);
                        user.removeHome(name);
                        onFailure.run();
                    }
                }, mainThread);
        return CreateResult.CREATED;
    }

    /**
     * Removes a home from cache and MySQL. Must be called on the main thread.
     *
     * @return the removed home, or {@code null} if it did not exist
     */
    public Home deleteHome(User user, String name, Runnable onFailure) {
        Home removed = user.removeHome(name);
        if (removed == null) {
            return null;
        }
        UUID owner = user.getUuid();
        database.update(connection -> repository.delete(connection, owner, removed.name()))
                .whenCompleteAsync((ignored, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to delete home '" + removed.name() + "' of " + owner, error);
                        if (!user.hasHome(removed.name())) {
                            user.putHome(removed);
                        }
                        onFailure.run();
                    }
                }, mainThread);
        return removed;
    }

    /**
     * Loads homes of any player (online or offline) straight from MySQL.
     */
    public CompletableFuture<List<Home>> loadHomes(UUID owner) {
        return database.query(connection -> repository.findByOwner(connection, owner));
    }

    /**
     * Asynchronous, chunk-loading-safe teleport. The future completes on the main thread.
     *
     * @return {@code false} if the world is not loaded or the teleport was cancelled
     */
    public CompletableFuture<Boolean> teleport(Player player, Home home) {
        Location location = home.toLocation();
        if (location == null) {
            return CompletableFuture.completedFuture(false);
        }
        return player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
}
