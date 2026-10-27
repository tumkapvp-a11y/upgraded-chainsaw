package dev.applesub.service;

import dev.applesub.AppleSubPlugin;
import dev.applesub.config.Settings;
import dev.applesub.data.Feature;
import dev.applesub.data.PlayerData;
import dev.applesub.feature.NightVision;
import dev.applesub.gui.MainMenu;
import dev.applesub.gui.Menu;
import dev.applesub.storage.Database;
import dev.applesub.storage.Database.NamedPlayer;
import dev.applesub.util.DurationUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class SubscriptionService {

    private final AppleSubPlugin plugin;

    public SubscriptionService(AppleSubPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    // ---- admin ----

    public void give(CommandSender sender, String targetName, long duration) {
        Database database = plugin.database();
        resolve(targetName, true).thenCompose(target -> {
            if (target == null) {
                return CompletableFuture.completedFuture(null);
            }
            PlayerData cached = plugin.dataManager().get(target.uuid());
            Database.PlayerRow defaults = (cached != null ? cached
                    : PlayerData.defaults(target.uuid(), target.name(), settings())).snapshot();
            return database.supply(() -> new Result(target,
                    database.extendSubscription(target.uuid(), target.name(), duration, defaults)));
        }).whenComplete((result, error) -> plugin.sync(() -> handle(sender, targetName, result, error, r -> {
            PlayerData cached = plugin.dataManager().get(r.target().uuid());
            if (cached != null) {
                cached.expiresAt(r.expiresAt());
            }
            TagResolver[] placeholders = {
                    Placeholder.unparsed("player", r.target().name()),
                    Placeholder.unparsed("duration", settings().formatDuration(duration)),
                    Placeholder.unparsed("time", settings().formatRemaining(r.expiresAt())),
                    Placeholder.unparsed("date", settings().formatDate(r.expiresAt()))
            };
            settings().send(sender, "give-success", placeholders);
            Player online = Bukkit.getPlayer(r.target().uuid());
            if (online != null && online != sender) {
                settings().send(online, "subscription-received", placeholders);
            }
            if (online != null) {
                settings().sound(online, "received");
                if (cached != null && cached.isActive(Feature.NIGHT_VISION)) {
                    NightVision.ensure(online);
                }
                refreshMenu(online, false);
            }
        })));
    }

    public void take(CommandSender sender, String targetName) {
        Database database = plugin.database();
        resolve(targetName, false).thenCompose(target -> {
            if (target == null) {
                return CompletableFuture.completedFuture(null);
            }
            return database.supply(() -> {
                Database.PlayerRow row = database.loadPlayer(target.uuid());
                long previous = row == null ? 0L : row.expiresAt();
                if (row != null) {
                    database.setExpiry(target.uuid(), 0L);
                }
                return new Result(target, previous);
            });
        }).whenComplete((result, error) -> plugin.sync(() -> handle(sender, targetName, result, error, r -> {
            Player online = Bukkit.getPlayer(r.target().uuid());
            PlayerData cached = plugin.dataManager().get(r.target().uuid());
            boolean wasActive = DurationUtil.isActive(r.expiresAt(), System.currentTimeMillis())
                    || (cached != null && cached.hasSubscription());
            if (cached != null && online != null) {
                endSubscription(online, cached, wasActive ? "subscription-taken" : null);
            } else if (cached != null) {
                cached.expiresAt(0L);
            }
            settings().send(sender, wasActive ? "take-success" : "take-not-active",
                    Placeholder.unparsed("player", r.target().name()));
        })));
    }

    public void check(CommandSender sender, String targetName) {
        Database database = plugin.database();
        resolve(targetName, false).thenCompose(target -> {
            if (target == null) {
                return CompletableFuture.completedFuture(null);
            }
            return database.supply(() -> {
                Database.PlayerRow row = database.loadPlayer(target.uuid());
                return new Result(target, row == null ? 0L : row.expiresAt());
            });
        }).whenComplete((result, error) -> plugin.sync(() -> handle(sender, targetName, result, error, r -> {
            if (DurationUtil.isActive(r.expiresAt(), System.currentTimeMillis())) {
                settings().send(sender, "check-active",
                        Placeholder.unparsed("player", r.target().name()),
                        Placeholder.unparsed("time", settings().formatRemaining(r.expiresAt())),
                        Placeholder.unparsed("date", settings().formatDate(r.expiresAt())));
            } else {
                settings().send(sender, "check-inactive", Placeholder.unparsed("player", r.target().name()));
            }
        })));
    }

    private record Result(NamedPlayer target, long expiresAt) {
    }

    private void handle(CommandSender sender, String targetName, Result result, Throwable error, Consumer<Result> onSuccess) {
        if (error != null) {
            plugin.getLogger().log(Level.WARNING, "Не удалось выполнить команду для " + targetName, error);
            settings().send(sender, "database-error");
        } else if (result == null) {
            settings().send(sender, "player-not-found", Placeholder.unparsed("player", targetName));
        } else {
            onSuccess.accept(result);
        }
    }

    /**
     * Finds a player by name: online, server cache, our table and finally (for give)
     * a regular offline-player lookup, which may query Mojang.
     */
    private CompletableFuture<NamedPlayer> resolve(String name, boolean allowLookup) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(new NamedPlayer(online.getUniqueId(), online.getName()));
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getName() != null) {
            return CompletableFuture.completedFuture(new NamedPlayer(cached.getUniqueId(), cached.getName()));
        }
        Database database = plugin.database();
        return database.supply(() -> {
            NamedPlayer stored = database.findByName(name);
            if (stored != null || !allowLookup || !name.matches("[A-Za-z0-9_.]{1,32}")) {
                return stored;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            return new NamedPlayer(offline.getUniqueId(), offline.getName() != null ? offline.getName() : name);
        });
    }

    // ---- player ----

    public void openMenu(Player player) {
        PlayerData data = plugin.dataManager().get(player.getUniqueId());
        if (data == null) {
            settings().send(player, "data-loading");
            return;
        }
        new MainMenu(plugin, player).open();
    }

    public void toggle(Player player, PlayerData data, Feature feature) {
        boolean enabled = !data.isEnabled(feature);
        data.setEnabled(feature, enabled);
        if (feature == Feature.NIGHT_VISION) {
            if (enabled) {
                NightVision.ensure(player);
            } else {
                NightVision.remove(player);
            }
        }
        plugin.dataManager().saveSettings(data);
        settings().sound(player, enabled ? "toggle-on" : "toggle-off");
        settings().actionBar(player, "feature-toggled",
                Placeholder.component("feature", settings().featureName(feature)),
                Placeholder.component("state", settings().state(enabled)));
    }

    public void onJoin(Player player, PlayerData data) {
        if (!data.hasSubscription()) {
            return;
        }
        if (data.isEnabled(Feature.NIGHT_VISION)) {
            NightVision.ensure(player);
        }
        settings().send(player, "join-active",
                Placeholder.unparsed("time", settings().formatRemaining(data.expiresAt())));
    }

    /** Called by the feature task once the expiry passes; the "expired" message is shown only once. */
    public void expire(Player player, PlayerData data) {
        long expiredAt = data.expiresAt();
        Database database = plugin.database();
        database.run(() -> database.clearExpired(data.uuid(), expiredAt));
        endSubscription(player, data, "subscription-expired");
    }

    private void endSubscription(Player player, PlayerData data, String messageKey) {
        boolean nightVision = data.isActive(Feature.NIGHT_VISION);
        data.expiresAt(0L);
        if (nightVision) {
            NightVision.remove(player);
        }
        refreshMenu(player, true);
        if (messageKey != null) {
            settings().send(player, messageKey);
            settings().sound(player, "error");
        }
    }

    /** Redraws an open menu; after losing the subscription, sub-menus fall back to the locked main menu. */
    private void refreshMenu(Player player, boolean lost) {
        if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        if (lost && !(menu instanceof MainMenu)) {
            new MainMenu(plugin, player).open();
        } else {
            menu.refresh();
        }
    }
}
