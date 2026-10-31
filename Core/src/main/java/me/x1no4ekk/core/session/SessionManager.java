package me.x1no4ekk.core.session;

import me.x1no4ekk.core.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single place where per-player state is released on quit.
 * Every manager that keys anything by a player's UUID registers a hook here; the {@link Player} passed to a
 * hook is only valid during the call and must not be stored. The user cache is unloaded last.
 */
public final class SessionManager implements Listener {

    private final Logger logger;
    private final UserManager userManager;
    private final List<Consumer<Player>> quitHooks = new CopyOnWriteArrayList<>();

    public SessionManager(Logger logger, UserManager userManager) {
        this.logger = logger;
        this.userManager = userManager;
    }

    public void onQuit(Consumer<Player> hook) {
        quitHooks.add(hook);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Consumer<Player> hook : quitHooks) {
            try {
                hook.accept(player);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Quit hook failed for " + player.getName(), e);
            }
        }
        userManager.unload(player.getUniqueId());
    }
}
