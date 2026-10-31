package me.x1no4ekk.core.teleport;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.combat.CombatManager;
import me.x1no4ekk.core.config.Settings;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Sounds;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Every Core teleport goes through here: warmup with a boss bar, cancel on move/damage, combat check,
 * /back recording and {@link Player#teleportAsync}. Pending warmups are keyed by UUID and released on
 * completion, cancellation and quit. Main thread only.
 */
public final class TeleportService implements Listener {

    public static final String BYPASS_WARMUP_PERMISSION = "core.teleport.bypass-warmup";
    public static final String BACK_ON_DEATH_PERMISSION = "core.back.death";

    private static final double MAX_MOVE_SQUARED = 0.5 * 0.5;

    private final CorePlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private BukkitTask ticker;

    private static final class Pending {
        private final Location destination;
        private final Location origin;
        private final TeleportOptions options;
        private final CompletableFuture<TeleportResult> future = new CompletableFuture<>();
        private final long startedAt = System.currentTimeMillis();
        private final long durationMillis;
        private final BossBar bar;
        private int lastShownSeconds = -1;

        private Pending(Location destination, Location origin, TeleportOptions options, long durationMillis, BossBar bar) {
            this.destination = destination;
            this.origin = origin;
            this.options = options;
            this.durationMillis = durationMillis;
            this.bar = bar;
        }
    }

    public TeleportService(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.sessions().onQuit(player -> cancel(player, TeleportResult.QUIT, null));
    }

    /**
     * Starts a teleport. The future completes on the main thread; failure messages (combat, cancel) are sent
     * here, the success message is up to the caller.
     */
    public CompletableFuture<TeleportResult> teleport(Player player, Location destination, TeleportOptions options) {
        if (destination == null || destination.getWorld() == null) {
            plugin.messages().send(player, "teleport.failed");
            return CompletableFuture.completedFuture(TeleportResult.FAILED);
        }
        if (options.checkCombat() && isBlockedByCombat(player)) {
            plugin.messages().send(player, "teleport.in-combat",
                    Placeholder.unparsed("seconds", String.valueOf(plugin.combatManager().getRemainingSeconds(player.getUniqueId()))));
            return CompletableFuture.completedFuture(TeleportResult.IN_COMBAT);
        }
        cancel(player, TeleportResult.CANCELLED, null);

        Settings.TeleportSettings config = plugin.settings().teleport();
        int warmup = config.warmupSeconds();
        if (!options.warmup() || warmup <= 0 || player.hasPermission(BYPASS_WARMUP_PERMISSION)) {
            return perform(player, destination.clone(), options);
        }

        BossBar bar = null;
        if (config.bossbarEnabled()) {
            bar = BossBar.bossBar(
                    Text.parse(config.bossbarTitle(), Placeholder.unparsed("seconds", String.valueOf(warmup))),
                    0F, config.bossbarColor(), config.bossbarOverlay());
            player.showBossBar(bar);
        }
        Pending entry = new Pending(destination.clone(), player.getLocation(), options, warmup * 1000L, bar);
        pending.put(player.getUniqueId(), entry);
        plugin.messages().send(player, "teleport.warmup", Placeholder.unparsed("seconds", String.valueOf(warmup)));
        ensureTicker();
        return entry.future;
    }

    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /**
     * Cancels a pending warmup.
     *
     * @param messageKey message to send, or {@code null} for none
     */
    public void cancel(Player player, TeleportResult result, String messageKey) {
        Pending entry = pending.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        if (entry.bar != null) {
            player.hideBossBar(entry.bar);
        }
        if (messageKey != null) {
            plugin.messages().send(player, messageKey);
        }
        entry.future.complete(result);
    }

    private boolean isBlockedByCombat(Player player) {
        return plugin.combatManager().isInCombat(player.getUniqueId())
                && !player.hasPermission(CombatManager.BYPASS_PERMISSION);
    }

    private CompletableFuture<TeleportResult> perform(Player player, Location destination, TeleportOptions options) {
        UUID uuid = player.getUniqueId();
        Location from = player.getLocation();
        return player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND)
                .thenApplyAsync(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        Player online = Bukkit.getPlayer(uuid);
                        if (online != null) {
                            plugin.messages().send(online, "teleport.failed");
                        }
                        return TeleportResult.FAILED;
                    }
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null) {
                        return TeleportResult.QUIT;
                    }
                    if (options.recordBack()) {
                        User user = plugin.userManager().getUser(uuid);
                        if (user != null) {
                            user.setLastLocation(from);
                        }
                    }
                    Settings.TeleportSettings config = plugin.settings().teleport();
                    if (config.invulnerabilityTicks() > 0) {
                        online.setNoDamageTicks(config.invulnerabilityTicks());
                    }
                    if (options.sound()) {
                        Sounds.play(online, config.sound());
                    }
                    return TeleportResult.SUCCESS;
                }, plugin.mainThread())
                .exceptionally(error -> {
                    plugin.getLogger().warning("Teleport of " + uuid + " failed: " + error.getMessage());
                    return TeleportResult.FAILED;
                });
    }

    private void ensureTicker() {
        if (ticker == null || ticker.isCancelled()) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        }
    }

    private void tick() {
        if (pending.isEmpty()) {
            ticker.cancel();
            ticker = null;
            return;
        }
        Settings.TeleportSettings config = plugin.settings().teleport();
        long now = System.currentTimeMillis();
        List<UUID> ids = new ArrayList<>(pending.keySet());
        for (UUID uuid : ids) {
            Pending entry = pending.get(uuid);
            if (entry == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                pending.remove(uuid);
                entry.future.complete(TeleportResult.QUIT);
                continue;
            }
            if (config.cancelOnMove() && moved(entry.origin, player.getLocation())) {
                cancel(player, TeleportResult.CANCELLED, "teleport.cancelled-move");
                continue;
            }
            long elapsed = now - entry.startedAt;
            if (elapsed >= entry.durationMillis) {
                pending.remove(uuid);
                if (entry.bar != null) {
                    player.hideBossBar(entry.bar);
                }
                perform(player, entry.destination, entry.options).thenAccept(entry.future::complete);
                continue;
            }
            if (entry.bar != null) {
                entry.bar.progress(Math.clamp((float) elapsed / entry.durationMillis, 0F, 1F));
                int secondsLeft = (int) ((entry.durationMillis - elapsed + 999) / 1000);
                if (secondsLeft != entry.lastShownSeconds) {
                    entry.lastShownSeconds = secondsLeft;
                    entry.bar.name(Text.parse(config.bossbarTitle(), Placeholder.unparsed("seconds", String.valueOf(secondsLeft))));
                }
            }
        }
    }

    private static boolean moved(Location origin, Location current) {
        return origin.getWorld() != current.getWorld() || origin.distanceSquared(current) > MAX_MOVE_SQUARED;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.settings().teleport().cancelOnDamage()
                && pending.containsKey(player.getUniqueId())) {
            cancel(player, TeleportResult.CANCELLED, "teleport.cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        cancel(player, TeleportResult.CANCELLED, null);
        if (player.hasPermission(BACK_ON_DEATH_PERMISSION)) {
            User user = plugin.userManager().getUser(player);
            if (user != null) {
                user.setLastLocation(player.getLocation());
            }
        }
    }

    /**
     * Cancels every warmup; called from onDisable.
     */
    public void shutdown() {
        for (UUID uuid : new ArrayList<>(pending.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            Pending entry = pending.remove(uuid);
            if (entry != null) {
                if (player != null && entry.bar != null) {
                    player.hideBossBar(entry.bar);
                }
                entry.future.complete(TeleportResult.CANCELLED);
            }
        }
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }
}
