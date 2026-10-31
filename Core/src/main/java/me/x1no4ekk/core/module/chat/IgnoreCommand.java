package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.user.User;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /ignore &lt;player&gt; toggles an ignore (online or offline player), /ignore list shows the list.
 * Ignores live in core_ignores; the cached {@link User} set is updated optimistically and rolled back on failure.
 */
final class IgnoreCommand extends BaseCommand {

    private static final String INSERT = "INSERT IGNORE INTO core_ignores (uuid, ignored_uuid) VALUES (?, ?)";
    private static final String DELETE = "DELETE FROM core_ignores WHERE uuid = ? AND ignored_uuid = ?";

    IgnoreCommand(CorePlugin plugin) {
        super(plugin, "ignore", ChatPermissions.IGNORE, "Игнорировать игрока");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 1) {
            throw fail("ignore.usage");
        }
        User user = requireUser(player);
        if (args[0].equalsIgnoreCase("list")) {
            showList(player, user);
            return;
        }
        Player online = findPlayer(player, args[0]);
        if (online != null) {
            toggle(player, user, online.getUniqueId(), online.getName());
            return;
        }
        UUID uuid = player.getUniqueId();
        String query = args[0];
        plugin.userManager().findByName(query).whenCompleteAsync((result, error) -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to look up player '" + query + "' for /ignore", error);
                messages().send(current, "general.database-error");
                return;
            }
            if (result.isEmpty()) {
                messages().send(current, "general.player-not-found", Placeholder.unparsed("player", query));
                return;
            }
            User currentUser = plugin.userManager().getUser(uuid);
            if (currentUser == null) {
                messages().send(current, "general.data-not-loaded");
                return;
            }
            toggle(current, currentUser, result.get().getKey(), result.get().getValue());
        }, plugin.mainThread());
    }

    /**
     * Main thread only.
     */
    private void toggle(Player player, User user, UUID target, String targetName) {
        UUID owner = player.getUniqueId();
        if (owner.equals(target)) {
            messages().send(player, "ignore.self");
            return;
        }
        if (user.isIgnoring(target)) {
            user.removeIgnored(target);
            persist(DELETE, owner, target, () -> {
                if (!user.isIgnoring(target)) {
                    user.addIgnored(target);
                }
            });
            messages().send(player, "ignore.removed", Placeholder.unparsed("player", targetName));
            return;
        }
        Player online = Bukkit.getPlayer(target);
        if (online != null && online.hasPermission(ChatPermissions.IGNORE_EXEMPT)) {
            messages().send(player, "ignore.exempt", Placeholder.unparsed("player", targetName));
            return;
        }
        user.addIgnored(target);
        persist(INSERT, owner, target, () -> {
            if (user.isIgnoring(target)) {
                user.removeIgnored(target);
            }
        });
        messages().send(player, "ignore.added", Placeholder.unparsed("player", targetName));
    }

    private void persist(String sql, UUID owner, UUID target, Runnable rollback) {
        plugin.database().update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, owner.toString());
                statement.setString(2, target.toString());
                statement.executeUpdate();
            }
        }).whenCompleteAsync((ignored, error) -> {
            if (error == null) {
                return;
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to save ignore " + owner + " -> " + target, error);
            rollback.run();
            Player player = Bukkit.getPlayer(owner);
            if (player != null) {
                messages().send(player, "general.database-error");
            }
        }, plugin.mainThread());
    }

    private void showList(Player player, User user) {
        Set<UUID> ignored = Set.copyOf(user.getIgnored());
        if (ignored.isEmpty()) {
            messages().send(player, "ignore.list-empty");
            return;
        }
        UUID uuid = player.getUniqueId();
        List<UUID> ids = List.copyOf(ignored);
        plugin.database().query(connection -> {
            String sql = "SELECT uuid, name FROM core_users WHERE uuid IN (" + "?,".repeat(ids.size() - 1) + "?)";
            Map<UUID, String> names = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < ids.size(); i++) {
                    statement.setString(i + 1, ids.get(i).toString());
                }
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        names.put(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
                    }
                }
            }
            return names;
        }).whenCompleteAsync((names, error) -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load ignore list of " + uuid, error);
                messages().send(current, "general.database-error");
                return;
            }
            List<String> list = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                list.add(names.getOrDefault(id, id.toString()));
            }
            list.sort(String.CASE_INSENSITIVE_ORDER);
            messages().send(current, "ignore.list",
                    Placeholder.unparsed("count", String.valueOf(list.size())),
                    Placeholder.unparsed("players", String.join(", ", list)));
        }, plugin.mainThread());
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(filter(List.of("list"), args[0]));
        options.addAll(playerNames(sender, args[0]));
        return options;
    }
}
