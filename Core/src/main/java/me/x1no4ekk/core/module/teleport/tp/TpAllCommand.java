package me.x1no4ekk.core.module.teleport.tp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.teleport.TeleportOptions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /tpall — pulls every online player the sender can see to the sender.
 */
public final class TpAllCommand extends BaseCommand {

    public TpAllCommand(CorePlugin plugin) {
        super(plugin, "tpall", "core.tpall", "Телепортировать к себе всех игроков");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length > 0) {
            throw fail("tp.tpall-usage");
        }
        Location destination = player.getLocation();
        String playerName = player.getName();
        int count = 0;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !plugin.vanishManager().canSee(player, other)) {
                continue;
            }
            UUID otherId = other.getUniqueId();
            plugin.teleportService().teleport(other, destination, TeleportOptions.ADMIN).thenAcceptAsync(result -> {
                Player moved = Bukkit.getPlayer(otherId);
                if (result == TeleportResult.SUCCESS && moved != null) {
                    messages().send(moved, "tp.all-target", Placeholder.unparsed("player", playerName));
                }
            }, plugin.mainThread());
            count++;
        }
        if (count == 0) {
            throw fail("tp.all-empty");
        }
        messages().send(player, "tp.all", Placeholder.unparsed("count", String.valueOf(count)));
    }
}
