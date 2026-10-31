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
 * /tpcancel [player] — withdraws the sender's own request to one player, or all of them.
 */
public final class TpCancelCommand extends BaseCommand {

    private final TpaManager manager;

    public TpCancelCommand(CorePlugin plugin, TpaManager manager) {
        super(plugin, "tpcancel", "core.tpa", "Отменить свой запрос на телепортацию");
        this.manager = manager;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length > 1) {
            throw fail("tpa.cancel-usage");
        }
        UUID senderId = player.getUniqueId();
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !manager.hasRequest(target.getUniqueId(), senderId)) {
                throw fail("tpa.no-outgoing-to", Placeholder.unparsed("player", args[0]));
            }
            cancel(player, target);
            return;
        }
        List<UUID> targets = manager.outgoingTargets(senderId);
        if (targets.isEmpty()) {
            throw fail("tpa.no-outgoing");
        }
        for (UUID targetId : targets) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                cancel(player, target);
            } else {
                manager.remove(targetId, senderId);
            }
        }
    }

    private void cancel(Player sender, Player target) {
        manager.remove(target.getUniqueId(), sender.getUniqueId());
        messages().send(sender, "tpa.cancelled", Placeholder.unparsed("player", target.getName()));
        messages().send(target, "tpa.cancelled-target", Placeholder.unparsed("player", sender.getName()));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID targetId : manager.outgoingTargets(player.getUniqueId())) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                names.add(target.getName());
            }
        }
        return filter(names, args[0]);
    }
}
