package me.x1no4ekk.core.combat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.config.Settings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat tag (anti-relog): PvP damage tags both players for N seconds. While tagged, teleports and most
 * commands are blocked and flight is suspended; leaving the server kills the player.
 * Tags are UUID-keyed and removed on expiry, death and quit.
 */
public final class CombatManager implements Listener {

    public static final String BYPASS_PERMISSION = "core.combat.bypass";

    private final CorePlugin plugin;
    /** Read from other threads through CoreApi, hence concurrent. */
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private final Set<UUID> suspendedFlight = ConcurrentHashMap.newKeySet();
    private BukkitTask ticker;

    public CombatManager(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.sessions().onQuit(player -> {
            taggedUntil.remove(player.getUniqueId());
            suspendedFlight.remove(player.getUniqueId());
        });
    }

    private Settings.CombatSettings config() {
        return plugin.settings().combat();
    }

    public boolean isInCombat(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    public int getRemainingSeconds(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /**
     * Tags (or re-tags) a player. Main thread only.
     */
    public void tag(Player player) {
        Settings.CombatSettings config = config();
        if (!config.enabled() || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean wasTagged = isInCombat(uuid);
        taggedUntil.put(uuid, System.currentTimeMillis() + config.durationSeconds() * 1000L);
        if (!wasTagged) {
            plugin.messages().send(player, "combat.tagged",
                    Placeholder.unparsed("seconds", String.valueOf(config.durationSeconds())));
            if (config.disableFlight() && player.getAllowFlight()) {
                player.setFlying(false);
                player.setAllowFlight(false);
                suspendedFlight.add(uuid);
            }
            if (config.disableElytra() && player.isGliding()) {
                player.setGliding(false);
            }
        }
        ensureTicker();
    }

    /**
     * Removes the tag and restores suspended flight. Main thread only.
     */
    public void untag(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        if (taggedUntil.remove(uuid) == null) {
            return;
        }
        if (suspendedFlight.remove(uuid)) {
            player.setAllowFlight(true);
        }
        if (notify) {
            plugin.messages().send(player, "combat.ended");
        }
    }

    private void ensureTicker() {
        if (ticker == null || ticker.isCancelled()) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
    }

    private void tick() {
        if (taggedUntil.isEmpty()) {
            ticker.cancel();
            ticker = null;
            return;
        }
        boolean actionbar = config().actionbar();
        for (UUID uuid : Set.copyOf(taggedUntil.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                taggedUntil.remove(uuid);
                suspendedFlight.remove(uuid);
                continue;
            }
            int left = getRemainingSeconds(uuid);
            if (left <= 0) {
                untag(player, true);
            } else if (actionbar) {
                player.sendActionBar(plugin.messages().get("combat.actionbar",
                        Placeholder.unparsed("seconds", String.valueOf(left))));
            }
        }
    }

    // ---------------------------------------------------------------- listeners

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config().enabled() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        tag(victim);
        tag(attacker);
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        ProjectileSource source = null;
        if (damager instanceof Projectile projectile) {
            source = projectile.getShooter();
        } else if (damager instanceof AreaEffectCloud cloud) {
            source = cloud.getSource();
        } else if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        return source instanceof Player player ? player : null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isInCombat(player.getUniqueId()) || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        String message = event.getMessage();
        int end = message.indexOf(' ');
        String label = (end < 0 ? message.substring(1) : message.substring(1, end)).toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        if (!config().allowedCommands().contains(label)) {
            event.setCancelled(true);
            plugin.messages().send(player, "combat.command-blocked",
                    Placeholder.unparsed("seconds", String.valueOf(getRemainingSeconds(player.getUniqueId()))));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && config().disableElytra() && event.getEntity() instanceof Player player
                && isInCombat(player.getUniqueId()) && !player.hasPermission(BYPASS_PERMISSION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        untag(event.getPlayer(), false);
    }

    /**
     * Runs before the session cleanup (MONITOR) so the player can still be killed.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isInCombat(player.getUniqueId()) || !config().punishOnQuit()) {
            return;
        }
        // Kicks, timeouts and shutdowns are not the player's fault.
        if (event.getReason() != PlayerQuitEvent.QuitReason.DISCONNECTED || Bukkit.isStopping()) {
            return;
        }
        taggedUntil.remove(player.getUniqueId());
        player.setHealth(0);
        Bukkit.getServer().sendMessage(plugin.messages().get("combat.quit-broadcast",
                Placeholder.unparsed("player", player.getName())));
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (UUID uuid : Set.copyOf(suspendedFlight)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setAllowFlight(true);
            }
        }
        suspendedFlight.clear();
        taggedUntil.clear();
    }
}
