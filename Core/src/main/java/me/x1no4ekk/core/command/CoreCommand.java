package me.x1no4ekk.core.command;

import me.x1no4ekk.core.CorePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * /core reload | /core horn [player] [amount]
 */
public final class CoreCommand implements TabExecutor {

    private final CorePlugin plugin;

    public CoreCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "core.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "horn" -> giveHorn(sender, args);
            default -> plugin.messages().send(sender, "core.usage");
        }
        return true;
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadSettings();
            plugin.messages().send(sender, "general.reloaded");
        } catch (Exception e) {
            // Previous settings stay active.
            sender.sendMessage(plugin.messages().get("general.database-error"));
            plugin.getLogger().severe("Reload failed, keeping previous settings: " + e.getMessage());
        }
    }

    private void giveHorn(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "general.player-not-found", Placeholder.unparsed("player", args[1]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "core.usage");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.clamp(Integer.parseInt(args[2]), 1, 64);
            } catch (NumberFormatException e) {
                plugin.messages().send(sender, "general.invalid-number", Placeholder.unparsed("value", args[2]));
                return;
            }
        }

        ItemStack horn = plugin.teleportHorn().create(1);
        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(horn.clone());
            leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        }
        plugin.messages().send(target, "core.horn-received");
        if (sender != target) {
            plugin.messages().send(sender, "core.horn-given",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("amount", String.valueOf(amount)));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Stream.of("reload", "horn"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("horn")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("horn")) {
            return filter(Stream.of("1", "16", "64"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
