package me.x1no4ekk.core.module.warp;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Paginated list of warps laid out by a {@link WarpConfig.ListMenu}.
 * Subclasses must call {@link #render()} at the end of their constructor.
 */
abstract class PagedWarpMenu extends WarpMenu {

    protected final WarpConfig.ListMenu layout;
    private List<Warp> warps;
    private int page;

    protected PagedWarpMenu(WarpModule module, UUID viewerId, WarpConfig.ListMenu layout, Component title, List<Warp> warps) {
        super(module, viewerId, layout.rows(), title);
        this.layout = layout;
        this.warps = List.copyOf(warps);
    }

    protected abstract void onWarpClick(Player player, ClickType click, Warp warp);

    protected abstract void onBack();

    /**
     * Replaces the snapshot (e.g. after a deletion) and re-renders, keeping the page when possible.
     */
    protected final void replaceWarps(List<Warp> newWarps) {
        this.warps = List.copyOf(newWarps);
        this.page = Math.min(page, pageCount() - 1);
        render();
    }

    private int pageCount() {
        int perPage = layout.contentSlots().size();
        return Math.max(1, (warps.size() + perPage - 1) / perPage);
    }

    protected final void render() {
        clear();
        List<Integer> contentSlots = layout.contentSlots();
        fill(layout.filler(), contentSlots);
        WarpConfig config = module.config();

        if (warps.isEmpty()) {
            setItem(layout.empty().slot(), button(layout.empty()));
        } else {
            int perPage = contentSlots.size();
            int start = page * perPage;
            int end = Math.min(warps.size(), start + perPage);
            for (int i = start; i < end; i++) {
                Warp warp = warps.get(i);
                ItemStack icon = WarpFormat.icon(config, warp, layout.warpItem(), layout.ownerHeads());
                setItem(contentSlots.get(i - start), icon, (player, event) -> onWarpClick(player, event.getClick(), warp));
            }
        }

        if (page > 0) {
            setItem(layout.previousPage().slot(), button(layout.previousPage()), (player, event) -> {
                page--;
                render();
            });
        }
        if (page < pageCount() - 1) {
            setItem(layout.nextPage().slot(), button(layout.nextPage()), (player, event) -> {
                page++;
                render();
            });
        }
        setItem(layout.back().slot(), button(layout.back()), (player, event) -> onBack());
    }
}
