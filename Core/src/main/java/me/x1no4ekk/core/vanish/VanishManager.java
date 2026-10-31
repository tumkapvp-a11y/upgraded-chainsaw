package me.x1no4ekk.core.vanish;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanish state of online players (persisted in core_users.vanish, re-applied on join).
 * Uses {@link Player#hidePlayer(org.bukkit.plugin.Plugin, Player)}; no metadata is attached to players.
 */
public final class VanishManager implements Listener {

    public static final String USE_PERMISSION = "core.vanish";
    public static final String SEE_PERMISSION = "core.vanish.see";

    private final CorePlugin plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishManager(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.sessions().onQuit(player -> vanished.remove(player.getUniqueId()));
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public Set<UUID> getVanished() {
        return Set.copyOf(vanished);
    }

    /**
     * @return whether {@code viewer} may see/target {@code target} in commands, lists and tab completion
     */
    public boolean canSee(CommandSender viewer, Player target) {
        if (!(viewer instanceof Player player) || player.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        return !vanished.contains(target.getUniqueId()) || player.hasPermission(SEE_PERMISSION);
    }

    /**
     * Main thread only.
     */
    public void setVanished(Player player, boolean value) {
        UUID uuid = player.getUniqueId();
        User user = plugin.userManager().getUser(uuid);
        if (user != null && user.isVanish() != value) {
            user.setVanish(value);
        }
        if (value) {
            vanished.add(uuid);
        } else {
            vanished.remove(uuid);
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(uuid)) {
                continue;
            }
            if (value && !other.hasPermission(SEE_PERMISSION)) {
                other.hidePlayer(plugin, player);
            } else {
                other.showPlayer(plugin, player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        if (!joiner.hasPermission(SEE_PERMISSION)) {
            for (UUID uuid : vanished) {
                Player hidden = Bukkit.getPlayer(uuid);
                if (hidden != null) {
                    joiner.hidePlayer(plugin, hidden);
                }
            }
        }
        User user = plugin.userManager().getUser(joiner);
        if (user != null && user.isVanish()) {
            if (joiner.hasPermission(USE_PERMISSION)) {
                setVanished(joiner, true);
                event.joinMessage(null);
                plugin.messages().send(joiner, "general.still-vanished");
            } else {
                user.setVanish(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (vanished.contains(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && vanished.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && vanished.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Makes everyone visible again so nobody stays hidden after the plugin is disabled.
     */
    public void shutdown() {
        for (UUID uuid : Set.copyOf(vanished)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, player);
            }
        }
        vanished.clear();
    }
}
