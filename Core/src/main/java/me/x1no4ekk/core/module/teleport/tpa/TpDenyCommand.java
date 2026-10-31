package me.x1no4ekk.core.module.teleport.tpa;

import me.x1no4ekk.core.CorePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tpdeny [player] (alias /tpno) — declines a request.
 */
public final class TpDenyCommand extends TpaReplyCommand {

    public TpDenyCommand(CorePlugin plugin, TpaManager manager) {
        super(plugin, manager, "tpdeny", "Отклонить запрос на телепортацию", "tpno");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player target = requirePlayer(sender);
        if (args.length > 1) {
            throw fail("tpa.deny-usage");
        }
        Player requester = takeRequest(target, args);
        messages().send(target, "tpa.denied", Placeholder.unparsed("player", requester.getName()));
        messages().send(requester, "tpa.denied-sender", Placeholder.unparsed("player", target.getName()));
    }
}
