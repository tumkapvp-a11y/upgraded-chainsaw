package me.x1no4ekk.core.module.inventory.invsee;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.module.inventory.InventoriesConfig;
import me.x1no4ekk.core.util.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.function.Supplier;

/**
 * /invsee &lt;player&gt; — opens the target's live inventory; read-only unless the viewer has
 * {@code core.invsee.modify} (enforced by {@link InvseeListener}).
 */
public final class InvseeCommand extends BaseCommand {

    public static final String PERMISSION = "core.invsee";
    public static final String MODIFY_PERMISSION = "core.invsee.modify";
    public static final String EXEMPT_PERMISSION = "core.invsee.exempt";

    private final Supplier<InventoriesConfig.InvseeSettings> settings;

    public InvseeCommand(CorePlugin plugin, Supplier<InventoriesConfig.InvseeSettings> settings) {
        super(plugin, "invsee", PERMISSION, "Просмотр инвентаря игрока");
        this.settings = settings;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender);
        if (args.length != 1) {
            throw fail("invsee.usage");
        }
        Player target = requireOnline(viewer, args[0]);
        if (target.equals(viewer)) {
            throw fail("invsee.self");
        }
        if (target.hasPermission(EXEMPT_PERMISSION)) {
            throw fail("invsee.exempt", Placeholder.unparsed("player", target.getName()));
        }

        viewer.openInventory(target.getInventory());
        if (!(viewer.getOpenInventory().getTopInventory() instanceof PlayerInventory)) {
            return; // the open was cancelled by another plugin
        }
        InventoriesConfig.InvseeSettings config = settings.get();
        Sounds.play(viewer, config.openSound());
        boolean modify = viewer.hasPermission(MODIFY_PERMISSION);
        messages().send(viewer, modify ? "invsee.opened" : "invsee.opened-read-only",
                Placeholder.unparsed("player", target.getName()));
        if (config.logUsage()) {
            plugin.getLogger().info(viewer.getName() + " opened the inventory of " + target.getName()
                    + (modify ? " (can modify)" : " (read-only)"));
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return playerNames(sender, args[0]);
        }
        return List.of();
    }
}
