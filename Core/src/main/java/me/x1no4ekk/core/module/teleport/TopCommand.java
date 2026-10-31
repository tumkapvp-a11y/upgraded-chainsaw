package me.x1no4ekk.core.module.teleport;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.teleport.TeleportOptions;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * /top — moves the player onto the highest block of their column.
 */
public final class TopCommand extends BaseCommand {

    public TopCommand(CorePlugin plugin) {
        super(plugin, "top", "core.top", "Подняться на самый верхний блок");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length > 0) {
            throw fail("top.usage");
        }
        Location current = player.getLocation();
        World world = current.getWorld();
        Block highest = world.getHighestBlockAt(current.getBlockX(), current.getBlockZ(), HeightMap.MOTION_BLOCKING);
        Material type = highest.getType();
        // Air means an empty column (void); water is fine to surface on, other hazards are not.
        if (type.isAir() || (type != Material.WATER && SafeSpots.isHazard(type, Set.of()))) {
            throw fail("top.unsafe");
        }
        if (highest.getY() + 1 <= current.getBlockY()) {
            throw fail("top.already");
        }
        Location destination = new Location(world, highest.getX() + 0.5, highest.getY() + 1, highest.getZ() + 0.5,
                current.getYaw(), current.getPitch());
        UUID uuid = player.getUniqueId();
        plugin.teleportService().teleport(player, destination, TeleportOptions.ADMIN).thenAccept(result -> {
            Player online = Bukkit.getPlayer(uuid);
            if (result == TeleportResult.SUCCESS && online != null) {
                messages().send(online, "top.teleported");
            }
        });
    }
}
