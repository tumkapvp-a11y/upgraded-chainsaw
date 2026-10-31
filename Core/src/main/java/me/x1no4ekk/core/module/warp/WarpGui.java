package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Main /warp menu: server warps, player warps and the viewer's own warps.
 */
final class WarpGui extends WarpMenu {

    WarpGui(WarpModule module, Player viewer) {
        super(module, viewer.getUniqueId(), module.config().mainMenu().rows(), Text.parse(module.config().mainMenu().title()));
        render(viewer);
    }

    private void render(Player viewer) {
        WarpConfig.MainMenu layout = module.config().mainMenu();
        WarpManager manager = module.manager();
        clear();
        fill(layout.filler(), List.of());

        setItem(layout.system().slot(),
                button(layout.system(), Placeholder.unparsed("size", String.valueOf(manager.serverWarps().size()))),
                (player, event) -> nextTick(p -> new SystemWarpGui(module, p.getUniqueId()).open(p)));

        setItem(layout.players().slot(),
                button(layout.players(), Placeholder.unparsed("size", String.valueOf(manager.playerWarps().size()))),
                (player, event) -> nextTick(p -> new PlayersWarpGui(module, p.getUniqueId()).open(p)));

        int limit = manager.getLimit(viewer);
        setItem(layout.my().slot(),
                button(layout.my(),
                        Placeholder.unparsed("size", String.valueOf(manager.countOwned(viewerId))),
                        Placeholder.unparsed("max", limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit))),
                (player, event) -> nextTick(p -> new MyWarpGui(module, p.getUniqueId()).open(p)));

        setItem(layout.close().slot(), button(layout.close()), (player, event) -> closeLater());
    }
}
