package me.x1no4ekk.core.module.inventory.enderchest;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /enderchest [player] (ec, echest) — own chest, or another player's (online or offline) with
 * {@code core.enderchest.others}; editing it additionally needs {@code core.enderchest.others.modify}.
 */
public final class EnderChestCommand extends BaseCommand {

    private final EnderChestService service;

    public EnderChestCommand(CorePlugin plugin, EnderChestService service) {
        super(plugin, "enderchest", EnderChestService.PERMISSION, "Эндер-сундук", "ec", "echest");
        this.service = service;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender);
        if (args.length > 1) {
            throw fail("enderchest.usage");
        }
        if (args.length == 0 || args[0].equalsIgnoreCase(viewer.getName())) {
            service.open(viewer, viewer.getUniqueId(), viewer.getName());
            return;
        }
        requirePermission(viewer, EnderChestService.OTHERS_PERMISSION);
        String name = args[0];
        Player online = findPlayer(viewer, name);
        if (online != null) {
            service.open(viewer, online.getUniqueId(), online.getName());
            return;
        }

        UUID viewerId = viewer.getUniqueId();
        plugin.userManager().findByName(name).whenCompleteAsync((found, error) -> {
            Player current = Bukkit.getPlayer(viewerId);
            if (current == null) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to look up player " + name, error);
                plugin.messages().send(current, "general.database-error");
                return;
            }
            if (found.isEmpty()) {
                plugin.messages().send(current, "general.player-not-found", Placeholder.unparsed("player", name));
                return;
            }
            Map.Entry<UUID, String> target = found.get();
            if (target.getKey().equals(viewerId)) {
                service.open(current, viewerId, current.getName());
            } else if (current.hasPermission(EnderChestService.OTHERS_PERMISSION)) {
                service.open(current, target.getKey(), target.getValue());
            }
        }, plugin.mainThread());
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission(EnderChestService.OTHERS_PERMISSION)) {
            return playerNames(sender, args[0]);
        }
        return List.of();
    }
}
