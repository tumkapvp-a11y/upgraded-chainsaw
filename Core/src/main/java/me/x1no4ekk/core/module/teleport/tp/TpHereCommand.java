package me.x1no4ekk.core.module.teleport.tp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.teleport.TeleportOptions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /tphere &lt;player&gt; — pulls a player to the sender.
 */
public final class TpHereCommand extends BaseCommand {

    public TpHereCommand(CorePlugin plugin) {
        super(plugin, "tphere", "core.tphere", "Телепортировать игрока к себе");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 1) {
            throw fail("tp.tphere-usage");
        }
        Player target = requireOnline(sender, args[0]);
        if (target.equals(player)) {
            throw fail("tp.self");
        }
        UUID playerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();
        String playerName = player.getName();
        String targetName = target.getName();
        plugin.teleportService().teleport(target, player.getLocation(), TeleportOptions.ADMIN).thenAccept(result -> {
            if (result != TeleportResult.SUCCESS) {
                return;
            }
            Player staff = Bukkit.getPlayer(playerId);
            if (staff != null) {
                messages().send(staff, "tp.here", Placeholder.unparsed("player", targetName));
            }
            Player moved = Bukkit.getPlayer(targetId);
            if (moved != null) {
                messages().send(moved, "tp.here-target", Placeholder.unparsed("player", playerName));
            }
        });
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? playerNames(sender, args[0]) : List.of();
    }
}
