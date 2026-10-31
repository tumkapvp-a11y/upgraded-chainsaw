package me.x1no4ekk.core.module.teleport.tpa;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared part of /tpaccept and /tpdeny: resolves which incoming request is answered and removes it.
 */
abstract class TpaReplyCommand extends BaseCommand {

    protected final TpaManager manager;

    protected TpaReplyCommand(CorePlugin plugin, TpaManager manager, String name, String description, String... aliases) {
        super(plugin, name, "core.tpa", description, aliases);
        this.manager = manager;
    }

    /**
     * Takes the request named in {@code args[0]} (or the newest one) out of the manager.
     *
     * @return the online requester
     */
    protected final Player takeRequest(Player target, String[] args) {
        UUID targetId = target.getUniqueId();
        if (args.length == 0) {
            UUID senderId = manager.latestSender(targetId);
            Player requester = senderId == null ? null : Bukkit.getPlayer(senderId);
            if (requester == null) {
                throw fail("tpa.no-requests");
            }
            manager.remove(targetId, senderId);
            return requester;
        }
        // No vanish filter: the target already knows who sent the request.
        Player requester = Bukkit.getPlayerExact(args[0]);
        if (requester == null || !manager.hasRequest(targetId, requester.getUniqueId())) {
            throw fail("tpa.no-request-from", Placeholder.unparsed("player", args[0]));
        }
        manager.remove(targetId, requester.getUniqueId());
        return requester;
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID senderId : manager.incomingSenders(player.getUniqueId())) {
            Player requester = Bukkit.getPlayer(senderId);
            if (requester != null) {
                names.add(requester.getName());
            }
        }
        return filter(names, args[0]);
    }
}
