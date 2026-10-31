package me.x1no4ekk.core.module.teleport.tpa;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.user.User;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tptoggle — enables or disables incoming /tpa requests (stored in the user's tpToggle flag).
 */
public final class TpToggleCommand extends BaseCommand {

    public TpToggleCommand(CorePlugin plugin) {
        super(plugin, "tptoggle", "core.tptoggle", "Включить или отключить запросы на телепортацию");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length > 0) {
            throw fail("tpa.toggle-usage");
        }
        User user = requireUser(player);
        boolean enabled = !user.isTpToggle();
        user.setTpToggle(enabled);
        messages().send(player, enabled ? "tpa.toggle-on" : "tpa.toggle-off");
    }
}
