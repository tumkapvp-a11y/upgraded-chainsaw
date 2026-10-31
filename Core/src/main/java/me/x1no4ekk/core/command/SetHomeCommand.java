package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.home.HomeManager;
import me.x1no4ekk.core.user.User;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /sethome &lt;name&gt;
 */
public final class SetHomeCommand extends BaseCommand {

    public SetHomeCommand(CorePlugin plugin) {
        super(plugin, "sethome", "core.sethome", "Создать точку дома");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 1) {
            throw fail("home.sethome-usage");
        }
        User user = requireUser(player);
        String name = args[0];
        UUID uuid = player.getUniqueId();
        HomeManager.CreateResult result = plugin.homeManager().createHome(player, user, name, () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                messages().send(online, "general.database-error");
            }
        });

        TagResolver home = Placeholder.unparsed("home", name);
        switch (result) {
            case CREATED -> messages().send(player, "home.created", home);
            case INVALID_NAME -> messages().send(player, "home.invalid-name");
            case ALREADY_EXISTS -> messages().send(player, "home.already-exists", home);
            case LIMIT_REACHED -> messages().send(player, "home.limit",
                    Placeholder.unparsed("limit", String.valueOf(plugin.homeManager().getHomeLimit(player))));
            case DISABLED_WORLD -> messages().send(player, "home.disabled-world");
            case SPECTATOR -> messages().send(player, "home.spectator");
        }
    }
}
