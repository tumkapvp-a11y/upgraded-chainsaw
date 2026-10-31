package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /core reload | /core horn [player] [amount]
 */
public final class AdminCommand extends BaseCommand {

    public AdminCommand(CorePlugin plugin) {
        super(plugin, "core", "core.admin", "Администрирование плагина Core");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            throw fail("core.usage");
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "horn" -> giveHorn(sender, args);
            default -> throw fail("core.usage");
        }
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadAll();
            messages().send(sender, "general.reloaded");
        } catch (Exception e) {
            plugin.getLogger().severe("Reload failed, keeping previous settings: " + e.getMessage());
            messages().send(sender, "general.reload-failed", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
        }
    }

    private void giveHorn(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? requireOnline(sender, args[1]) : requirePlayer(sender);
        int amount = args.length >= 3 ? parseInt(args[2], 1, 64) : 1;

        ItemStack horn = plugin.teleportHorn().create(1);
        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(horn.clone());
            leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        }
        messages().send(target, "core.horn-received");
        if (sender != target) {
            messages().send(sender, "core.horn-given",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("amount", String.valueOf(amount)));
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "horn"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("horn")) {
            return playerNames(sender, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("horn")) {
            return filter(List.of("1", "16", "64"), args[2]);
        }
        return List.of();
    }
}
