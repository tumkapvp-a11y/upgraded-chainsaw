package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.home.Home;
import me.x1no4ekk.core.user.User;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /delhome &lt;name&gt;
 */
public final class DelHomeCommand implements TabExecutor {

    private final CorePlugin plugin;

    public DelHomeCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (args.length != 1) {
            plugin.messages().send(player, "home.delhome-usage");
            return true;
        }
        User user = plugin.userManager().getUser(player);
        if (user == null) {
            plugin.messages().send(player, "general.data-not-loaded");
            return true;
        }

        UUID uuid = player.getUniqueId();
        Home removed = plugin.homeManager().deleteHome(user, args[0], () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                plugin.messages().send(online, "general.database-error");
            }
        });
        if (removed == null) {
            plugin.messages().send(player, "home.not-found", Placeholder.unparsed("home", args[0]));
        } else {
            plugin.messages().send(player, "home.deleted", Placeholder.unparsed("home", removed.name()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        User user = plugin.userManager().getUser(player);
        if (user == null) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return user.getHomesSorted().stream()
                .map(Home::name)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
