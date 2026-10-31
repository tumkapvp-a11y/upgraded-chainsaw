package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.home.HomeManager;
import me.x1no4ekk.core.user.User;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * /sethome &lt;name&gt;
 */
public final class SetHomeCommand implements TabExecutor {

    private final CorePlugin plugin;

    public SetHomeCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (args.length != 1) {
            plugin.messages().send(player, "home.sethome-usage");
            return true;
        }
        User user = plugin.userManager().getUser(player);
        if (user == null) {
            plugin.messages().send(player, "general.data-not-loaded");
            return true;
        }

        String name = args[0];
        UUID uuid = player.getUniqueId();
        HomeManager.CreateResult result = plugin.homeManager().createHome(player, user, name, () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                plugin.messages().send(online, "general.database-error");
            }
        });

        var home = Placeholder.unparsed("home", name);
        switch (result) {
            case CREATED -> plugin.messages().send(player, "home.created", home);
            case INVALID_NAME -> plugin.messages().send(player, "home.invalid-name");
            case ALREADY_EXISTS -> plugin.messages().send(player, "home.already-exists", home);
            case LIMIT_REACHED -> plugin.messages().send(player, "home.limit",
                    Placeholder.unparsed("limit", String.valueOf(plugin.homeManager().getHomeLimit(player))));
            case DISABLED_WORLD -> plugin.messages().send(player, "home.disabled-world");
            case SPECTATOR -> plugin.messages().send(player, "home.spectator");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
