package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /home is intentionally disabled: homes are reachable only through the Teleport Horn.
 */
public final class HomeCommand implements TabExecutor {

    private final CorePlugin plugin;

    public HomeCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        plugin.messages().send(sender, "home.disabled");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
