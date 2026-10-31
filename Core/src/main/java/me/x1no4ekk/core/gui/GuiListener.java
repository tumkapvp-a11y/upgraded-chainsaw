package me.x1no4ekk.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes inventory events to {@link Gui} holders. Stateless: no list of open menus is kept.
 */
public final class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (holder(event.getInventory().getHolder(false)) instanceof Gui gui) {
            gui.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (holder(event.getInventory().getHolder(false)) instanceof Gui gui) {
            gui.handleDrag(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (holder(event.getInventory().getHolder(false)) instanceof Gui gui) {
            gui.onClose(event);
        }
    }

    private static InventoryHolder holder(InventoryHolder holder) {
        return holder;
    }

    /**
     * Closes every open plugin menu; used on disable so no view outlives the plugin's classes.
     */
    public static void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Gui) {
                player.closeInventory();
            }
        }
    }
}
