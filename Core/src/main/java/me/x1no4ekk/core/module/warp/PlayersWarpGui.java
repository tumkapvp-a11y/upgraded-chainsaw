package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.UUID;

/**
 * Player warps sorted by rating, then visits. Left click teleports, right click opens the grade menu.
 */
final class PlayersWarpGui extends PagedWarpMenu {

    PlayersWarpGui(WarpModule module, UUID viewerId) {
        super(module, viewerId, module.config().playersMenu(), Text.parse(module.config().playersMenu().title()),
                module.manager().playerWarps());
        render();
    }

    @Override
    protected void onWarpClick(Player player, ClickType click, Warp warp) {
        if (click.isRightClick()) {
            if (warp.isOwnedBy(player.getUniqueId())) {
                module.plugin().messages().send(player, "warp.grade-own");
                return;
            }
            String name = warp.name();
            nextTick(viewer -> {
                Warp current = module.manager().get(name);
                if (current == null || current.system()) {
                    viewer.closeInventory();
                    module.plugin().messages().send(viewer, "warp.not-found", Placeholder.unparsed("warp", name));
                    return;
                }
                new WarpGradeGui(module, viewer.getUniqueId(), current).open(viewer);
            });
            return;
        }
        if (click.isLeftClick()) {
            teleportLater(warp.name());
        }
    }

    @Override
    protected void onBack() {
        backToMainLater();
    }
}
