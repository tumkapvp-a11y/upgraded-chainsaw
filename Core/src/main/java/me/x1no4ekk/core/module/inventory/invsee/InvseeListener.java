package me.x1no4ekk.core.module.inventory.invsee;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * Makes another player's live inventory read-only for viewers without {@code core.invsee.modify}.
 * Stateless: the view itself tells whose inventory is open.
 */
public final class InvseeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (isReadOnly(event.getView().getTopInventory(), event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (isReadOnly(event.getView().getTopInventory(), event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    private static boolean isReadOnly(Inventory top, HumanEntity viewer) {
        return isForeign(top, viewer) && !viewer.hasPermission(InvseeCommand.MODIFY_PERMISSION);
    }

    private static boolean isForeign(Inventory top, HumanEntity viewer) {
        return top instanceof PlayerInventory inventory
                && inventory.getHolder() != null
                && !inventory.getHolder().getUniqueId().equals(viewer.getUniqueId());
    }

    /**
     * Closes every view of the target's inventory; called when the target quits, before their data is saved.
     */
    public static void closeViewersOf(Player target) {
        UUID targetId = target.getUniqueId();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getOpenInventory().getTopInventory() instanceof PlayerInventory inventory
                    && inventory.getHolder() != null
                    && inventory.getHolder().getUniqueId().equals(targetId)
                    && !viewer.getUniqueId().equals(targetId)) {
                viewer.closeInventory();
            }
        }
    }

    /**
     * Closes all views of other players' inventories (plugin disable: the read-only guard goes away).
     */
    public static void closeAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (isForeign(viewer.getOpenInventory().getTopInventory(), viewer)) {
                viewer.closeInventory();
            }
        }
    }
}
