package me.x1no4ekk.core.module.inventory.kit;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.gui.Gui;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.util.ItemBuilder;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kit selection menu: icons show the viewer's status for each kit.
 * Left click claims, right click opens a read-only preview ({@link KitContentGui}).
 * <p>
 * Holds only the immutable config snapshot it was rendered from; kits are re-resolved by id on click.
 */
public final class KitGui extends Gui {

    private final CorePlugin plugin;
    private final KitService service;
    private final KitsConfig config;

    public KitGui(CorePlugin plugin, KitService service, Player viewer) {
        this(plugin, service, service.config(), viewer);
    }

    private KitGui(CorePlugin plugin, KitService service, KitsConfig config, Player viewer) {
        super(config.gui().rows(), Text.parse(config.gui().title()));
        this.plugin = plugin;
        this.service = service;
        this.config = config;
        render(viewer);
    }

    private void render(Player viewer) {
        clear();
        KitsConfig.GuiSettings gui = config.gui();
        if (gui.filler() != null) {
            ItemStack filler = ItemBuilder.of(gui.filler()).hideTooltip().build();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                setItem(slot, filler);
            }
        }
        User user = plugin.userManager().getUser(viewer);
        for (Kit kit : config.kits().values()) {
            String kitId = kit.id();
            setItem(kit.slot(), icon(kit, service.state(viewer, user, kit)), (player, event) -> {
                // Only plain mouse clicks act: drop/number keys/middle click must not claim a kit by accident.
                if (event.isLeftClick() || event.isRightClick()) {
                    onClick(player, kitId, event.isRightClick());
                }
            });
        }
    }

    private ItemStack icon(Kit kit, KitService.KitState state) {
        KitsConfig.GuiSettings gui = config.gui();
        TagResolver resolvers = TagResolver.resolver(
                Placeholder.component("kit", kit.displayComponent()),
                Placeholder.component("price", service.priceText(kit.price())),
                Placeholder.component("cooldown", service.cooldownText(kit.cooldownSeconds())),
                Placeholder.unparsed("time", Durations.formatMillis(state.remainingMillis()))
        );
        List<String> lore = new ArrayList<>(kit.iconLore());
        lore.addAll(gui.info());
        lore.addAll(gui.status(state.status()));
        lore.addAll(gui.actions());
        return ItemBuilder.of(kit.iconMaterial())
                .name(kit.iconName() != null ? kit.iconName() : gui.iconName(), resolvers)
                .lore(lore, resolvers)
                .glint(kit.iconGlint())
                .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                .build();
    }

    private void onClick(Player clicker, String kitId, boolean preview) {
        UUID viewerId = clicker.getUniqueId();
        // Giving items and switching menus is done on the next tick, outside InventoryClickEvent.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || viewer.getOpenInventory().getTopInventory().getHolder(false) != this) {
                return;
            }
            Kit kit = service.config().kit(kitId);
            if (kit == null) {
                plugin.messages().send(viewer, "kit.not-found", Placeholder.unparsed("kit", kitId));
                viewer.closeInventory();
                return;
            }
            if (preview) {
                new KitContentGui(plugin, service, kit).open(viewer);
                return;
            }
            boolean claimed = service.claim(viewer, kit);
            if (claimed && config.closeOnClaim()) {
                viewer.closeInventory();
            } else {
                render(viewer);
            }
        });
    }
}
