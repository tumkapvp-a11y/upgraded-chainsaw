package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All warps live in memory as immutable records (server data, bounded by the per-player limits).
 * Writes go to the serial MySQL writer; the cache is updated optimistically and rolled back on failure.
 * Mutating methods must be called on the main thread.
 */
final class WarpManager {

    enum CreateResult {
        CREATED, INVALID_NAME, ALREADY_EXISTS, LIMIT_REACHED, DISABLED_WORLD, SPECTATOR, NOT_ENOUGH_MONEY, ECONOMY_UNAVAILABLE
    }

    static final String UNLIMITED_PERMISSION = "core.warps.unlimited";
    static final String LIMIT_PERMISSION_PREFIX = "core.warps.";

    private static final Comparator<Warp> BY_NAME = Comparator.comparing(Warp::name, String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<Warp> BY_POPULARITY = Comparator.comparingDouble(Warp::rating).reversed()
            .thenComparing(Comparator.comparingInt(Warp::visits).reversed())
            .thenComparing(BY_NAME);

    private final CorePlugin plugin;
    private final Logger logger;
    private final DatabaseManager database;
    private final WarpRepository repository;
    private final Supplier<WarpConfig> config;
    /** Lower-case name → warp; MySQL compares warp names case-insensitively. */
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();

    WarpManager(CorePlugin plugin, WarpRepository repository, Supplier<WarpConfig> config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.database = plugin.database();
        this.repository = repository;
        this.config = config;
    }

    /**
     * Blocking initial load; only called from the module's enable().
     */
    void loadAll() throws SQLException {
        List<Warp> loaded = database.execute(repository::loadAll);
        warps.clear();
        for (Warp warp : loaded) {
            warps.put(warp.key(), warp);
        }
    }

    // ---------------------------------------------------------------- lookup

    @Nullable Warp get(String name) {
        return warps.get(Warp.key(name));
    }

    List<String> names() {
        return warps.values().stream().sorted(BY_NAME).map(Warp::name).toList();
    }

    List<Warp> serverWarps() {
        return warps.values().stream().filter(Warp::system).sorted(BY_NAME).toList();
    }

    /**
     * Player warps, best rated first, then most visited.
     */
    List<Warp> playerWarps() {
        return warps.values().stream().filter(warp -> !warp.system()).sorted(BY_POPULARITY).toList();
    }

    List<Warp> warpsOf(UUID owner) {
        return warps.values().stream().filter(warp -> !warp.system() && warp.isOwnedBy(owner)).sorted(BY_NAME).toList();
    }

    int countOwned(UUID owner) {
        int count = 0;
        for (Warp warp : warps.values()) {
            if (!warp.system() && warp.isOwnedBy(owner)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Highest limit among {@code core.warps.<group>} permissions; {@code core.warps.unlimited} removes the cap.
     */
    int getLimit(Player player) {
        if (player.hasPermission(UNLIMITED_PERMISSION)) {
            return Integer.MAX_VALUE;
        }
        WarpConfig current = config.get();
        int limit = current.defaultLimit();
        for (Map.Entry<String, Integer> entry : current.limits().entrySet()) {
            if (entry.getValue() > limit && player.hasPermission(LIMIT_PERMISSION_PREFIX + entry.getKey())) {
                limit = entry.getValue();
            }
        }
        return limit;
    }

    boolean isValidName(String name) {
        return name.length() <= WarpConfig.MAX_NAME_LENGTH && config.get().namePattern().matcher(name).matches();
    }

    // ---------------------------------------------------------------- mutations

    /**
     * Validates, charges the creation price (player warps) and creates a warp at the player's position.
     *
     * @param onFailure runs on the main thread if the database write fails (cache rolled back, price refunded)
     */
    CreateResult create(Player player, String name, boolean server, Runnable onFailure) {
        WarpConfig current = config.get();
        if (!isValidName(name)) {
            return CreateResult.INVALID_NAME;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return CreateResult.SPECTATOR;
        }
        Location location = player.getLocation();
        if (!server && current.disabledWorlds().contains(location.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return CreateResult.DISABLED_WORLD;
        }
        if (warps.containsKey(Warp.key(name))) {
            return CreateResult.ALREADY_EXISTS;
        }
        UUID uuid = player.getUniqueId();
        if (!server && countOwned(uuid) >= getLimit(player)) {
            return CreateResult.LIMIT_REACHED;
        }
        double price = server ? 0 : current.price();
        if (price > 0) {
            if (!plugin.vault().hasEconomy()) {
                return CreateResult.ECONOMY_UNAVAILABLE;
            }
            if (!plugin.vault().has(player, price) || !plugin.vault().withdraw(player, price)) {
                return CreateResult.NOT_ENOUGH_MONEY;
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        Material icon = hand.isEmpty() ? current.defaultIcon() : hand.getType();
        Warp warp = Warp.create(name, server ? null : uuid, server ? null : player.getName(), server, location, icon);
        warps.put(warp.key(), warp);
        database.update(connection -> repository.insert(connection, warp))
                .whenCompleteAsync((ignored, error) -> {
                    if (error == null) {
                        return;
                    }
                    logger.log(Level.SEVERE, "Failed to save warp '" + warp.name() + "'", error);
                    warps.computeIfPresent(warp.key(), (key, cached) -> cached.sameAs(warp) ? null : cached);
                    if (price > 0) {
                        plugin.vault().deposit(Bukkit.getOfflinePlayer(uuid), price);
                    }
                    onFailure.run();
                }, plugin.mainThread());
        return CreateResult.CREATED;
    }

    /**
     * Removes a warp and its grades.
     *
     * @return the removed warp, or {@code null} if it did not exist
     */
    @Nullable Warp delete(String name, Runnable onFailure) {
        Warp removed = warps.remove(Warp.key(name));
        if (removed == null) {
            return null;
        }
        database.updateInTransaction(connection -> repository.delete(connection, removed.name()))
                .whenCompleteAsync((ignored, error) -> {
                    if (error == null) {
                        return;
                    }
                    logger.log(Level.SEVERE, "Failed to delete warp '" + removed.name() + "'", error);
                    warps.putIfAbsent(removed.key(), removed);
                    onFailure.run();
                }, plugin.mainThread());
        return removed;
    }

    /**
     * Counts a successful visit; a failed write is logged and the cached counter rolled back.
     */
    void recordVisit(String name) {
        Warp updated = warps.computeIfPresent(Warp.key(name), (key, warp) -> warp.withVisits(warp.visits() + 1));
        if (updated == null) {
            return;
        }
        database.update(connection -> repository.incrementVisits(connection, updated.name()))
                .whenCompleteAsync((ignored, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to count a visit of warp '" + updated.name() + "'", error);
                        warps.computeIfPresent(updated.key(),
                                (key, warp) -> warp.sameAs(updated) ? warp.withVisits(warp.visits() - 1) : warp);
                    }
                }, plugin.mainThread());
    }

    /**
     * Stores (or replaces) a player's grade and refreshes the cached aggregate from MySQL.
     * The aggregate is re-read on the serial writer so concurrent grades are applied in order.
     *
     * @return completes on the main thread once the cache is updated; fails if the write failed (already logged)
     */
    CompletableFuture<Void> grade(Warp warp, UUID grader, int grade) {
        AtomicReference<WarpRepository.Rating> result = new AtomicReference<>();
        long now = System.currentTimeMillis();
        return database.update(connection -> {
                    repository.upsertGrade(connection, warp.name(), grader, grade, now);
                    result.set(repository.loadRating(connection, warp.name()));
                })
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to save grade of warp '" + warp.name() + "' by " + grader, error);
                    }
                })
                .thenAcceptAsync(ignored -> {
                    WarpRepository.Rating rating = result.get();
                    warps.computeIfPresent(warp.key(),
                            (key, cached) -> cached.sameAs(warp) ? cached.withRating(rating.average(), rating.count()) : cached);
                }, plugin.mainThread());
    }

    /**
     * Last known name of any player from core_users.
     */
    CompletableFuture<Optional<String>> findUserName(UUID uuid) {
        return database.query(connection -> repository.findUserName(connection, uuid));
    }

    List<Warp> snapshot() {
        return new ArrayList<>(warps.values());
    }
}
