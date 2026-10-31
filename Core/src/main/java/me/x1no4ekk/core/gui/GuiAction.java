package me.x1no4ekk.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Click handler bound to a single slot of a {@link Gui}.
 */
@FunctionalInterface
public interface GuiAction {

    void onClick(Player player, InventoryClickEvent event);
}
