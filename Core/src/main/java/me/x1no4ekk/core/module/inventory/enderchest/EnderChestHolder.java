package me.x1no4ekk.core.module.inventory.enderchest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Holder of one ender chest page. Shared by every viewer of that page, so it carries no per-viewer state.
 */
public final class EnderChestHolder implements InventoryHolder {

    private final EnderChestSession session;
    private final int page;
    private Inventory inventory;

    EnderChestHolder(EnderChestSession session, int page) {
        this.session = session;
        this.page = page;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public EnderChestSession session() {
        return session;
    }

    /**
     * @return 1-based page number
     */
    public int page() {
        return page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
