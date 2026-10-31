package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.gui.Gui;
import me.x1no4ekk.core.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base of every warp menu. Holds only the module, the viewer's UUID and immutable snapshots.
 */
abstract class WarpMenu extends Gui {

    protected final WarpModule module;
    protected final UUID viewerId;

    protected WarpMenu(WarpModule module, UUID viewerId, int rows, Component title) {
        super(rows, title);
        this.module = module;
        this.viewerId = viewerId;
    }

    /**
     * Fills every slot except {@code keepEmpty} with the filler pane.
     */
    protected final void fill(WarpConfig.Filler filler, Collection<Integer> keepEmpty) {
        if (!filler.enabled()) {
            return;
        }
        Set<Integer> skip = Set.copyOf(keepEmpty);
        ItemStack pane = ItemBuilder.of(filler.material()).hideTooltip().build();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!skip.contains(slot)) {
                setItem(slot, pane);
            }
        }
    }

    protected static ItemStack button(WarpConfig.Button button, TagResolver... resolvers) {
        return ItemBuilder.of(button.material())
                .name(button.name(), resolvers)
                .lore(button.lore(), resolvers)
                .build();
    }

    /**
     * Runs on the next tick with the re-resolved viewer: inventories must not be opened or closed
     * inside InventoryClickEvent.
     */
    protected final void nextTick(Consumer<Player> task) {
        Bukkit.getScheduler().runTask(module.plugin(), () -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                task.accept(viewer);
            }
        });
    }

    protected final void closeLater() {
        nextTick(Player::closeInventory);
    }

    /**
     * Closes the menu and teleports to the current version of the warp (it may have been deleted meanwhile).
     */
    protected final void teleportLater(String warpName) {
        nextTick(viewer -> {
            viewer.closeInventory();
            Warp warp = module.manager().get(warpName);
            if (warp == null) {
                module.plugin().messages().send(viewer, "warp.not-found", Placeholder.unparsed("warp", warpName));
                return;
            }
            module.teleporter().teleport(viewer, warp);
        });
    }

    /**
     * Back to the main warp menu, or just closes if the viewer cannot use /warp (opened via /serverwarp).
     */
    protected final void backToMainLater() {
        nextTick(viewer -> {
            if (viewer.hasPermission(WarpCommand.PERMISSION)) {
                new WarpGui(module, viewer).open(viewer);
            } else {
                viewer.closeInventory();
            }
        });
    }
}
