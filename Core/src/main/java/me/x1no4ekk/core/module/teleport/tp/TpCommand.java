package me.x1no4ekk.core.module.teleport.tp;

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
 * /tp &lt;player&gt; [target] — staff teleport to a player, or of one player to another.
 * Overrides vanilla /tp, so {@code /tp x y z ...} is forwarded to /tppos.
 */
public final class TpCommand extends BaseCommand {

    public static final String OTHERS_PERMISSION = "core.tp.others";

    private final TpPosCommand tpPos;

    public TpCommand(CorePlugin plugin, TpPosCommand tpPos) {
        super(plugin, "tp", "core.tp", "Телепортация к игроку");
        this.tpPos = tpPos;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length >= 3 && args.length <= 6 && TpPosCommand.isNumber(args[0])) {
            Player player = requirePlayer(sender);
            requirePermission(sender, TpPosCommand.PERMISSION);
            tpPos.teleportToPosition(player, args);
            return;
        }
        if (args.length == 1) {
            Player player = requirePlayer(sender);
            Player target = requireOnline(sender, args[0]);
            if (target.equals(player)) {
                throw fail("tp.self");
            }
            teleport(sender, player, target);
            return;
        }
        if (args.length == 2) {
            requirePermission(sender, OTHERS_PERMISSION);
            Player who = requireOnline(sender, args[0]);
            Player target = requireOnline(sender, args[1]);
            if (who.equals(target)) {
                throw fail("tp.same-player");
            }
            teleport(sender, who, target);
            return;
        }
        throw fail("tp.usage");
    }

    private void teleport(CommandSender sender, Player who, Player target) {
        boolean self = who.equals(sender);
        SenderRef senderRef = SenderRef.of(sender);
        UUID whoId = who.getUniqueId();
        String whoName = who.getName();
        String targetName = target.getName();
        plugin.teleportService().teleport(who, target.getLocation(), TeleportOptions.ADMIN).thenAccept(result -> {
            if (result != TeleportResult.SUCCESS) {
                return;
            }
            Player moved = Bukkit.getPlayer(whoId);
            if (moved != null) {
                messages().send(moved, "tp.teleported", Placeholder.unparsed("player", targetName));
            }
            CommandSender staff = senderRef.resolve();
            if (!self && staff != null) {
                messages().send(staff, "tp.teleported-other",
                        Placeholder.unparsed("player", whoName),
                        Placeholder.unparsed("target", targetName));
            }
        });
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 || (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION))) {
            return playerNames(sender, args[args.length - 1]);
        }
        return List.of();
    }
}
