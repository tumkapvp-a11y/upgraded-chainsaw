package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /delwarp &lt;name&gt; — own warps; other players' and server warps need core.delwarp.others.
 * Also usable from the console.
 */
final class DelWarpCommand extends BaseCommand {

    static final String PERMISSION = "core.delwarp";
    static final String OTHERS_PERMISSION = "core.delwarp.others";

    private final WarpModule module;

    DelWarpCommand(WarpModule module) {
        super(module.plugin(), "delwarp", PERMISSION, "Удалить варп", "deletewarp");
        this.module = module;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            throw fail("warp.delwarp-usage");
        }
        Warp warp = module.manager().get(args[0]);
        if (warp == null) {
            throw fail("warp.not-found", Placeholder.unparsed("warp", args[0]));
        }
        boolean own = sender instanceof Player player && warp.isOwnedBy(player.getUniqueId());
        if (!own && !sender.hasPermission(OTHERS_PERMISSION)) {
            throw fail("warp.not-owner", Placeholder.unparsed("warp", warp.name()));
        }

        UUID playerId = sender instanceof Player player ? player.getUniqueId() : null;
        Warp removed = module.manager().delete(warp.name(), () -> {
            CommandSender target = playerId == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(playerId);
            if (target != null) {
                messages().send(target, "general.database-error");
            }
        });
        if (removed == null) {
            throw fail("warp.not-found", Placeholder.unparsed("warp", args[0]));
        }
        messages().send(sender, "warp.deleted", Placeholder.unparsed("warp", removed.name()));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        if (sender.hasPermission(OTHERS_PERMISSION)) {
            return filter(module.manager().names(), args[0]);
        }
        if (sender instanceof Player player) {
            return filter(module.manager().warpsOf(player.getUniqueId()).stream().map(Warp::name).toList(), args[0]);
        }
        return List.of();
    }
}
