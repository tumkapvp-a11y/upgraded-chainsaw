package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /warp — opens the warp menu; /warp &lt;name&gt; — teleports to a warp.
 */
final class WarpCommand extends BaseCommand {

    static final String PERMISSION = "core.warp";

    private final WarpModule module;

    WarpCommand(WarpModule module) {
        super(module.plugin(), "warp", PERMISSION, "Варпы сервера и игроков", "warps");
        this.module = module;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length == 0) {
            new WarpGui(module, player).open(player);
            return;
        }
        if (args.length != 1) {
            throw fail("warp.warp-usage");
        }
        Warp warp = module.manager().get(args[0]);
        if (warp == null) {
            throw fail("warp.not-found", Placeholder.unparsed("warp", args[0]));
        }
        module.teleporter().teleport(player, warp);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(module.manager().names(), args[0]);
        }
        return List.of();
    }
}
