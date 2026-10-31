package me.x1no4ekk.core.module.inventory.kit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Delivers first-join kits to brand-new players.
 */
public final class KitListener implements Listener {

    private final KitService service;

    public KitListener(KitService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.scheduleFirstJoin(event.getPlayer());
    }
}
