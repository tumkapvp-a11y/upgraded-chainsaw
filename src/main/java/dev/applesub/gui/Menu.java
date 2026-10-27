package dev.applesub.gui;

import dev.applesub.AppleSubPlugin;
import dev.applesub.data.PlayerData;
import dev.applesub.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

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

    protected abstract void render();

    /** Click inside this menu. The event is already cancelled. */
    public abstract void onTopClick(InventoryClickEvent event);

    /** Click inside the player's own inventory while this menu is open. The event is already cancelled. */
    public void onBottomClick(InventoryClickEvent event) {
    }

    /** Player data if the subscription is still active, otherwise closes the menu and returns null. */
    protected PlayerData activeData() {
        PlayerData data = plugin.dataManager().get(viewer.getUniqueId());
        if (data == null || !data.hasSubscription()) {
            later(viewer::closeInventory);
            plugin.settings().send(viewer, "no-subscription");
            return null;
        }
        return data;
    }

    /** Inventory changes from inside a click handler must run on the next tick. */
    protected void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    protected void click() {
        plugin.settings().sound(viewer, "menu-click");
    }

    /** Red / pink checkered frame around the menu. */
    protected void frame() {
        int rows = inventory.getSize() / 9;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                inventory.setItem(slot, pane(row, column));
            }
        }
    }

    protected static org.bukkit.inventory.ItemStack pane(int row, int column) {
        return Items.pane((row + column) % 2 == 0 ? Material.RED_STAINED_GLASS_PANE : Material.PINK_STAINED_GLASS_PANE);
    }
}
