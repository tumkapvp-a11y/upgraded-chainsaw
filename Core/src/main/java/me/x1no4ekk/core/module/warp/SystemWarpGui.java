package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.UUID;

/**
 * Server warps (/serverwarp or the main menu). Click teleports.
 */
final class SystemWarpGui extends PagedWarpMenu {

    SystemWarpGui(WarpModule module, UUID viewerId) {
        super(module, viewerId, module.config().systemMenu(), Text.parse(module.config().systemMenu().title()),
                module.manager().serverWarps());
        render();
    }

    @Override
    protected void onWarpClick(Player player, ClickType click, Warp warp) {
        teleportLater(warp.name());
    }

    @Override
    protected void onBack() {
        backToMainLater();
    }
}
