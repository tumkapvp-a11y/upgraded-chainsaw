package me.x1no4ekk.core.module.teleport.spawn;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.module.teleport.SenderRef;
import me.x1no4ekk.core.teleport.TeleportOptions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /spawn [player] — teleports to the server spawn; sending another player uses staff options (no warmup).
 */
public final class SpawnCommand extends BaseCommand {

    public static final String OTHERS_PERMISSION = "core.spawn.others";

    private final SpawnManager spawns;

    public SpawnCommand(CorePlugin plugin, SpawnManager spawns) {
        super(plugin, "spawn", "core.spawn", "Телепортация на спавн");
        this.spawns = spawns;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length > 1) {
            throw fail("spawn.usage");
        }
        Player target = targetOrSelf(sender, args, 0, OTHERS_PERMISSION);
        boolean self = target.equals(sender);
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        String senderName = sender.getName();
        SenderRef senderRef = SenderRef.of(sender);
        TeleportOptions options = self ? TeleportOptions.PLAYER : TeleportOptions.ADMIN;

        plugin.teleportService().teleport(target, spawns.spawnLocation(), options).thenAccept(result -> {
            if (result != TeleportResult.SUCCESS) {
                return;
            }
            Player moved = Bukkit.getPlayer(targetId);
            if (self) {
                if (moved != null) {
                    messages().send(moved, "spawn.teleported");
                }
                return;
            }
            if (moved != null) {
                messages().send(moved, "spawn.teleported-by", Placeholder.unparsed("player", senderName));
            }
            CommandSender staff = senderRef.resolve();
            if (staff != null) {
                messages().send(staff, "spawn.teleported-other", Placeholder.unparsed("player", targetName));
            }
        });
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission(OTHERS_PERMISSION)) {
            return playerNames(sender, args[0]);
        }
        return List.of();
    }
}
