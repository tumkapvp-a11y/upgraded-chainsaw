package me.x1no4ekk.core.module.inventory.kit;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * /kit — kit menu; /kit &lt;id&gt; — claim; admin: /kit create &lt;id&gt; [cooldown], /kit delete &lt;id&gt;,
 * /kit give &lt;id&gt; &lt;player&gt;.
 */
public final class KitCommand extends BaseCommand {

    private static final List<String> ADMIN_SUBCOMMANDS = List.of("create", "delete", "give");

    private final KitService service;
    private final KitStorage storage;

    @FunctionalInterface
    private interface StorageAction {
        KitsConfig run() throws IOException, InvalidConfigurationException;
    }

    public KitCommand(CorePlugin plugin, KitService service, KitStorage storage) {
        super(plugin, "kit", KitService.USE_PERMISSION, "Наборы предметов");
        this.service = service;
        this.storage = storage;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Player player = requirePlayer(sender);
            requireUser(player);
            if (service.config().kits().isEmpty()) {
                throw fail("kit.none");
            }
            new KitGui(plugin, service, player).open(player);
            return;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBCOMMANDS.contains(first) && sender.hasPermission(KitService.ADMIN_PERMISSION)) {
            switch (first) {
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                default -> give(sender, args);
            }
            return;
        }
        if (args.length != 1) {
            throw fail(usageKey(sender));
        }
        Player player = requirePlayer(sender);
        service.claim(player, requireKit(args[0]));
    }

    private void create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 2 || args.length > 3) {
            throw fail("kit.admin-usage");
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!KitsConfig.isValidId(id)) {
            throw fail("kit.invalid-id", Placeholder.unparsed("kit", args[1]));
        }
        KitsConfig current = service.config();
        if (current.kit(id) != null) {
            throw fail("kit.already-exists", Placeholder.unparsed("kit", id));
        }
        long cooldown = args.length == 3 ? parseInt(args[2], -1, Integer.MAX_VALUE) : current.createCooldownSeconds();

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.isEmpty()) {
                items.add(item.clone());
            }
        }
        if (items.isEmpty()) {
            throw fail("kit.create-empty");
        }
        int slot = current.freeSlot();
        if (slot < 0) {
            throw fail("kit.gui-full");
        }

        KitsConfig updated = editFile(sender, () -> storage.create(id, slot, cooldown, items.getFirst().getType(), items));
        service.apply(updated);
        messages().send(sender, "kit.created",
                Placeholder.unparsed("kit", id),
                Placeholder.unparsed("items", String.valueOf(items.size())),
                Placeholder.component("cooldown", service.cooldownText(cooldown)));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            throw fail("kit.admin-usage");
        }
        String id = requireKit(args[1]).id();
        KitsConfig updated = editFile(sender, () -> storage.delete(id));
        if (updated == null) {
            throw fail("kit.not-in-file", Placeholder.unparsed("kit", id));
        }
        service.apply(updated);
        messages().send(sender, "kit.deleted", Placeholder.unparsed("kit", id));
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length != 3) {
            throw fail("kit.admin-usage");
        }
        Kit kit = requireKit(args[1]);
        Player target = requireOnline(sender, args[2]);
        KitService.GiveResult result = service.give(target, kit);
        if (result == KitService.GiveResult.NO_SPACE) {
            throw fail("kit.give-no-space",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.component("kit", kit.displayComponent()));
        }
        messages().send(sender, "kit.given",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.component("kit", kit.displayComponent()));
        if (!target.equals(sender)) {
            messages().send(target, "kit.received", Placeholder.component("kit", kit.displayComponent()));
        }
        if (result == KitService.GiveResult.DROPPED) {
            messages().send(target, "kit.dropped");
        }
    }

    private Kit requireKit(String raw) {
        Kit kit = service.config().kit(raw);
        if (kit == null) {
            throw fail("kit.not-found", Placeholder.unparsed("kit", raw));
        }
        return kit;
    }

    private KitsConfig editFile(CommandSender sender, StorageAction action) {
        try {
            return action.run();
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update " + KitStorage.FILE_NAME + " for " + sender.getName(), e);
            throw fail("kit.file-error", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
        }
    }

    private static String usageKey(CommandSender sender) {
        return sender.hasPermission(KitService.ADMIN_PERMISSION) ? "kit.admin-usage" : "kit.usage";
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission(KitService.ADMIN_PERMISSION);
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (admin) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
            for (Kit kit : service.config().kits().values()) {
                if (admin || sender.hasPermission(kit.permission())) {
                    options.add(kit.id());
                }
            }
            return filter(options, args[0]);
        }
        if (!admin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("delete") || sub.equals("give"))) {
            return filter(service.config().kits().keySet(), args[1]);
        }
        if (args.length == 3 && sub.equals("give")) {
            return playerNames(sender, args[2]);
        }
        return List.of();
    }
}
