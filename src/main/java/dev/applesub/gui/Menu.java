package dev.applesub.gui;

import dev.applesub.AppleSubPlugin;
import dev.applesub.data.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class Menu implements InventoryHolder {

    protected final AppleSubPlugin plugin;
    protected final Player viewer;
    private final Inventory inventory;

    protected Menu(AppleSubPlugin plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = plugin.getServer().createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    /** Redraws the menu in place, e.g. after the subscription changed. */
    public void refresh() {
        render();
    }

    protected abstract void render();

    /** Click inside this menu. The event is already cancelled. */
    public abstract void onTopClick(InventoryClickEvent event);

    /** Click inside the player's own inventory while this menu is open. The event is already cancelled. */
    public void onBottomClick(InventoryClickEvent event) {
    }

    /** Player data if the subscription is active; otherwise tells the player and returns null. */
    protected PlayerData activeData() {
        PlayerData data = plugin.dataManager().get(viewer.getUniqueId());
        if (data != null && data.hasSubscription()) {
            return data;
        }
        plugin.settings().send(viewer, "feature-locked");
        plugin.settings().sound(viewer, "error");
        onLocked();
        return null;
    }

    /** A click needed a subscription the viewer does not have. */
    protected void onLocked() {
        later(() -> new MainMenu(plugin, viewer).open());
    }

    /** Inventory changes from inside a click handler must run on the next tick. */
    protected void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    protected void click() {
        plugin.settings().sound(viewer, "menu-click");
    }

    /** Places the decorative panes from config; null entries stay empty. */
    protected void applyLayout(ItemStack[] layout, int offset) {
        for (int i = 0; i < layout.length && offset + i < inventory.getSize(); i++) {
            if (layout[i] != null) {
                inventory.setItem(offset + i, layout[i]);
            }
        }
    }
}
