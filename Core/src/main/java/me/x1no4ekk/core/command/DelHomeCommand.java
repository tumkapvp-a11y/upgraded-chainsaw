package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.home.Home;
import me.x1no4ekk.core.user.User;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /delhome &lt;name&gt;
 */
public final class DelHomeCommand extends BaseCommand {

    public DelHomeCommand(CorePlugin plugin) {
        super(plugin, "delhome", "core.delhome", "Удалить точку дома");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 1) {
            throw fail("home.delhome-usage");
        }
        User user = requireUser(player);
        UUID uuid = player.getUniqueId();
        Home removed = plugin.homeManager().deleteHome(user, args[0], () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                messages().send(online, "general.database-error");
            }
        });
        if (removed == null) {
            throw fail("home.not-found", Placeholder.unparsed("home", args[0]));
        }
        messages().send(player, "home.deleted", Placeholder.unparsed("home", removed.name()));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        User user = plugin.userManager().getUser(player);
        if (user == null) {
            return List.of();
        }
        return filter(user.getHomesSorted().stream().map(Home::name).toList(), args[0]);
    }
}
