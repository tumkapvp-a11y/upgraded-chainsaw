package me.x1no4ekk.core.module.teleport.tpa;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.teleport.TeleportOptions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /tpaccept [player] — accepts a request; the requester is teleported to the accepting player.
 */
public final class TpAcceptCommand extends TpaReplyCommand {

    public TpAcceptCommand(CorePlugin plugin, TpaManager manager) {
        super(plugin, manager, "tpaccept", "Принять запрос на телепортацию");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player target = requirePlayer(sender);
        if (args.length > 1) {
            throw fail("tpa.accept-usage");
        }
        Player requester = takeRequest(target, args);
        String targetName = target.getName();
        String requesterName = requester.getName();
        UUID targetId = target.getUniqueId();
        UUID requesterId = requester.getUniqueId();

        messages().send(target, "tpa.accepted", Placeholder.unparsed("player", requesterName));
        messages().send(requester, "tpa.accepted-sender", Placeholder.unparsed("player", targetName));

        plugin.teleportService().teleport(requester, target.getLocation(), TeleportOptions.PLAYER).thenAcceptAsync(result -> {
            if (result != TeleportResult.SUCCESS) {
                return;
            }
            Player movedPlayer = Bukkit.getPlayer(requesterId);
            if (movedPlayer != null) {
                messages().send(movedPlayer, "tpa.teleported", Placeholder.unparsed("player", targetName));
            }
            Player host = Bukkit.getPlayer(targetId);
            if (host != null) {
                messages().send(host, "tpa.teleported-target", Placeholder.unparsed("player", requesterName));
            }
        }, plugin.mainThread());
    }
}
