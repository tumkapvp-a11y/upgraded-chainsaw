package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import org.bukkit.command.CommandSender;

/**
 * /home is intentionally disabled: homes are reachable only through the Teleport Horn.
 */
public final class HomeCommand extends BaseCommand {

    public HomeCommand(CorePlugin plugin) {
        super(plugin, "home", null, "Телепортация домой работает только через Рог телепортации", "homes");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        messages().send(sender, "home.disabled");
    }
}
