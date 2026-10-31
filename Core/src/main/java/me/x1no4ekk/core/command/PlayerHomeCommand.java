package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.gui.HomeGui;
import me.x1no4ekk.core.home.Home;
import me.x1no4ekk.core.user.User;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * /playerhome &lt;player&gt; [home] — opens another player's homes or teleports to one of them.
 * Works for offline players by reading MySQL asynchronously.
 */
public final class PlayerHomeCommand extends BaseCommand {

    private record Target(UUID uuid, String name, List<Home> homes) {
    }

    public PlayerHomeCommand(CorePlugin plugin) {
        super(plugin, "playerhome", "core.playerhome", "Точки дома другого игрока");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 1 || args.length > 2) {
            throw fail("home.playerhome-usage");
        }
        String targetName = args[0];
        String homeName = args.length == 2 ? args[1] : null;
        UUID viewerId = player.getUniqueId();

        resolveTarget(targetName).whenCompleteAsync((target, error) -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load homes of " + targetName, error);
                plugin.messages().send(viewer, "general.database-error");
                return;
            }
            if (target.isEmpty()) {
                plugin.messages().send(viewer, "general.player-not-found", Placeholder.unparsed("player", targetName));
                return;
            }
            handle(viewer, target.get(), homeName);
        }, plugin.mainThread());
    }

    private void handle(Player viewer, Target target, String homeName) {
        if (target.homes().isEmpty()) {
            plugin.messages().send(viewer, "home.player-empty", Placeholder.unparsed("player", target.name()));
            return;
        }
        if (homeName == null) {
            HomeGui.other(plugin, target.uuid(), target.name(), target.homes()).open(viewer);
            return;
        }
        Home home = target.homes().stream()
                .filter(h -> h.name().equalsIgnoreCase(homeName))
                .findFirst()
                .orElse(null);
        if (home == null) {
            plugin.messages().send(viewer, "home.not-found", Placeholder.unparsed("home", homeName));
            return;
        }
        plugin.homeTeleporter().teleportToOther(viewer, target.name(), home);
    }

    /**
     * Online players come from the cache; offline players are looked up in MySQL.
     */
    private CompletableFuture<Optional<Target>> resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            User user = plugin.userManager().getUser(online);
            if (user != null) {
                return CompletableFuture.completedFuture(Optional.of(new Target(user.getUuid(), user.getName(), user.getHomesSorted())));
            }
        }
        return plugin.userManager().findByName(name).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<Target>empty());
            }
            Map.Entry<UUID, String> entry = found.get();
            return plugin.homeManager().loadHomes(entry.getKey())
                    .thenApply(homes -> Optional.of(new Target(entry.getKey(), entry.getValue(), homes)));
        });
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return playerNames(sender, args[0]);
        }
        if (args.length == 2) {
            Player target = findPlayer(sender, args[0]);
            User user = target == null ? null : plugin.userManager().getUser(target);
            if (user == null) {
                return List.of();
            }
            return filter(user.getHomesSorted().stream().map(Home::name).toList(), args[1]);
        }
        return List.of();
    }
}
