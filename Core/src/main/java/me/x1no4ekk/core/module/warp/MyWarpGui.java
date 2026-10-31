package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.UUID;

/**
 * The viewer's own warps. Left click teleports, Shift + right click deletes.
 */
final class MyWarpGui extends PagedWarpMenu {

    MyWarpGui(WarpModule module, UUID viewerId) {
        super(module, viewerId, module.config().myMenu(), Text.parse(module.config().myMenu().title()),
                module.manager().warpsOf(viewerId));
        render();
    }

    @Override
    protected void onWarpClick(Player player, ClickType click, Warp warp) {
        if (click == ClickType.SHIFT_RIGHT) {
            delete(player, warp);
            return;
        }
        if (click.isLeftClick()) {
            teleportLater(warp.name());
        }
    }

    private void delete(Player player, Warp warp) {
        if (!player.hasPermission(DelWarpCommand.PERMISSION)) {
            module.plugin().messages().send(player, "general.no-permission");
            return;
        }
        UUID uuid = player.getUniqueId();
        Warp removed = module.manager().delete(warp.name(), () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                module.plugin().messages().send(online, "general.database-error");
            }
        });
        if (removed == null) {
            module.plugin().messages().send(player, "warp.not-found", Placeholder.unparsed("warp", warp.name()));
        } else {
            module.plugin().messages().send(player, "warp.deleted", Placeholder.unparsed("warp", removed.name()));
        }
        replaceWarps(module.manager().warpsOf(viewerId));
    }

    @Override
    protected void onBack() {
        backToMainLater();
    }
}
