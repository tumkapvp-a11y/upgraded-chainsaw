package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /msg (/m) &lt;player&gt; &lt;message&gt;
 */
final class MsgCommand extends BaseCommand {

    private final PrivateMessageService service;

    MsgCommand(CorePlugin plugin, PrivateMessageService service) {
        super(plugin, "msg", ChatPermissions.MSG, "Личное сообщение игроку", "m");
        this.service = service;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 2) {
            throw fail("msg.usage");
        }
        Player target = requireOnline(player, args[0]);
        service.send(player, target, joinArgs(args, 1));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? playerNames(sender, args[0]) : List.of();
    }
}
