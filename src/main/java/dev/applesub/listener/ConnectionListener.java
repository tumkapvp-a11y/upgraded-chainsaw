package dev.applesub.listener;

import dev.applesub.AppleSubPlugin;
import dev.applesub.data.PlayerData;
import dev.applesub.data.PlayerDataManager;
import dev.applesub.feature.AntiFireInjector;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public final class ConnectionListener implements Listener {

    private final AppleSubPlugin plugin;

    public ConnectionListener(AppleSubPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads data before the player enters the world so features work from the first tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        PlayerDataManager manager = plugin.dataManager();
        try {
            manager.cache(manager.loadNow(event.getUniqueId(), event.getName()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось загрузить данные " + event.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    public void handleJoin(Player player) {
        AntiFireInjector antiFire = plugin.antiFire();
        if (antiFire != null) {
            antiFire.inject(player);
        }
        PlayerDataManager manager = plugin.dataManager();
        PlayerData data = manager.get(player.getUniqueId());
        if (data != null) {
            plugin.service().onJoin(player, data);
            return;
        }
        manager.loadAsync(player.getUniqueId(), player.getName()).whenComplete((loaded, error) -> plugin.sync(() -> {
            if (error != null || !player.isOnline()) {
                return;
            }
            manager.cache(loaded);
            plugin.service().onJoin(player, loaded);
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.dataManager().unload(event.getPlayer().getUniqueId());
    }
}
