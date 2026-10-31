package me.x1no4ekk.core.module.inventory.kit;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.gui.Gui;
import me.x1no4ekk.core.util.ItemBuilder;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, paginated preview of a kit's items with a button back to {@link KitGui}.
 * Every click is cancelled by {@link Gui}, so the displayed copies can never be taken.
 */
public final class KitContentGui extends Gui {

    private final CorePlugin plugin;
    private final KitService service;
    private final KitsConfig.ContentSettings settings;
    private final List<ItemStack> items;
    private int page;

    public KitContentGui(CorePlugin plugin, KitService service, Kit kit) {
        this(plugin, service, service.config().content(), kit);
    }

    private KitContentGui(CorePlugin plugin, KitService service, KitsConfig.ContentSettings settings, Kit kit) {
        super(settings.rows(), Text.parse(settings.title(), Placeholder.component("kit", kit.displayComponent())));
        this.plugin = plugin;
        this.service = service;
        this.settings = settings;
        this.items = kit.copyItems();
        render();
    }

    private int pageCount() {
        int perPage = settings.itemSlots();
        return Math.max(1, (items.size() + perPage - 1) / perPage);
    }

    private void render() {
        clear();
        int perPage = settings.itemSlots();
        int start = page * perPage;
        int end = Math.min(items.size(), start + perPage);
        for (int i = start; i < end; i++) {
            setItem(i - start, items.get(i).clone());
        }

        if (settings.filler() != null) {
            ItemStack filler = ItemBuilder.of(settings.filler()).hideTooltip().build();
            for (int slot = perPage; slot < inventory.getSize(); slot++) {
                setItem(slot, filler);
            }
        }

        TagResolver pages = TagResolver.resolver(
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pageCount()))
        );
        setItem(settings.back().slot(), settings.back().build(pages), (player, event) -> back(player));
        if (page > 0) {
            setItem(settings.previousPage().slot(), settings.previousPage().build(pages), (player, event) -> {
                page--;
                render();
            });
        }
        if (page < pageCount() - 1) {
            setItem(settings.nextPage().slot(), settings.nextPage().build(pages), (player, event) -> {
                page++;
                render();
            });
        }
    }

    private void back(Player clicker) {
        UUID viewerId = clicker.getUniqueId();
        // Menus must not be switched inside InventoryClickEvent.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || viewer.getOpenInventory().getTopInventory().getHolder(false) != this) {
                return;
            }
            if (plugin.userManager().getUser(viewer) == null) {
                viewer.closeInventory();
                plugin.messages().send(viewer, "general.data-not-loaded");
                return;
            }
            new KitGui(plugin, service, viewer).open(viewer);
        });
    }
}
