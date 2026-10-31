package me.x1no4ekk.core.module.inventory.workstation;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * /workbench, /anvil, /carttable, /etable, /grindstone, /loom, /smithtable, /stonecutter — one instance per workstation.
 */
public final class WorkstationCommand extends BaseCommand {

    private final Workstation workstation;
    private final Supplier<String> title;

    /**
     * @param title current MiniMessage title from inventories.yml (read on every use, so /core reload applies)
     */
    public WorkstationCommand(CorePlugin plugin, Workstation workstation, Supplier<String> title) {
        super(plugin, workstation.label(), workstation.permission(), workstation.displayName(), workstation.aliases());
        this.workstation = workstation;
        this.title = title;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 0) {
            throw fail("workstation.usage", Placeholder.unparsed("command", name()));
        }
        workstation.open(player, Text.parse(title.get()));
    }
}
