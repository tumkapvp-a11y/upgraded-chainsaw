package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.command.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /serverwarp — opens the server warp menu directly.
 */
final class ServerWarpCommand extends BaseCommand {

    static final String PERMISSION = "core.serverwarp";

    private final WarpModule module;

    ServerWarpCommand(WarpModule module) {
        super(module.plugin(), "serverwarp", PERMISSION, "Варпы сервера", "serverwarps");
        this.module = module;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 0) {
            throw fail("warp.serverwarp-usage");
        }
        new SystemWarpGui(module, player.getUniqueId()).open(player);
    }
}
