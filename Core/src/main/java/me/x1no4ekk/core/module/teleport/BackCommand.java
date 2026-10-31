package me.x1no4ekk.core.module.teleport;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.teleport.TeleportOptions;
import me.x1no4ekk.core.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /back — returns to the point recorded before the last teleport (or the death point with core.back.death).
 */
public final class BackCommand extends BaseCommand {

    public BackCommand(CorePlugin plugin) {
        super(plugin, "back", "core.back", "Вернуться на предыдущую локацию");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length > 0) {
            throw fail("back.usage");
        }
        User user = requireUser(player);
        Location destination = user.getLastLocation();
        if (destination == null) {
            throw fail("back.not-found");
        }
        UUID uuid = player.getUniqueId();
        plugin.teleportService().teleport(player, destination, TeleportOptions.PLAYER).thenAccept(result -> {
            Player online = Bukkit.getPlayer(uuid);
            if (result == TeleportResult.SUCCESS && online != null) {
                messages().send(online, "back.teleported");
            }
        });
    }
}
