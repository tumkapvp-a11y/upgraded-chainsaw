package me.x1no4ekk.core.user;

import me.x1no4ekk.core.config.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads users on pre-login (async thread) and unloads them on quit.
 */
public final class UserListener implements Listener {

    private final UserManager userManager;
    private final Messages messages;
    private final Logger logger;
    private final IntSupplier loadTimeoutSeconds;

    public UserListener(UserManager userManager, Messages messages, Logger logger, IntSupplier loadTimeoutSeconds) {
        this.userManager = userManager;
        this.messages = messages;
        this.logger = logger;
        this.loadTimeoutSeconds = loadTimeoutSeconds;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        if (userManager.isLoaded(uuid)) {
            // Same account still online: its quit-save runs first and the join handler reloads.
            return;
        }
        try {
            userManager.loadBlocking(uuid, event.getName(), loadTimeoutSeconds.getAsInt());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.log(Level.SEVERE, "Could not load data of " + event.getName() + " (" + uuid + "); login denied", e);
            // Denying is safer than letting the player in with empty data that would overwrite MySQL.
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.get("general.load-failed-kick"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            userManager.discardIfOffline(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        User user = userManager.getUser(player);
        if (user == null) {
            userManager.loadAsync(player.getUniqueId(), name).thenAccept(loaded -> {
                if (loaded != null) {
                    applyJoin(loaded, name);
                }
            });
            return;
        }
        applyJoin(user, name);
    }

    private static void applyJoin(User user, String name) {
        user.setName(name);
        user.setLastJoin(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        userManager.unload(event.getPlayer().getUniqueId());
    }
}
