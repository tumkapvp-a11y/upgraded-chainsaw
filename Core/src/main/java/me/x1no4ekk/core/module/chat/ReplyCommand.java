package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /reply (/r) &lt;message&gt; — answers the last conversation partner.
 */
final class ReplyCommand extends BaseCommand {

    private final PrivateMessageService service;

    ReplyCommand(CorePlugin plugin, PrivateMessageService service) {
        super(plugin, "reply", ChatPermissions.MSG, "Ответить на последнее личное сообщение", "r");
        this.service = service;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length == 0) {
            throw fail("msg.reply-usage");
        }
        UUID partnerId = service.partner(player.getUniqueId());
        // The partner took part in the conversation, so replying is allowed even if they are vanished now.
        Player partner = partnerId == null ? null : Bukkit.getPlayer(partnerId);
        if (partner == null) {
            throw fail("msg.reply-not-found");
        }
        service.send(player, partner, joinArgs(args, 0));
    }
}
