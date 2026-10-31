package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /setwarp &lt;name&gt; [server]
 */
final class SetWarpCommand extends BaseCommand {

    static final String PERMISSION = "core.setwarp";
    static final String SERVER_PERMISSION = "core.warp.server";
    private static final String SERVER_FLAG = "server";

    private final WarpModule module;

    SetWarpCommand(WarpModule module) {
        super(module.plugin(), "setwarp", PERMISSION, "Создать варп", "createwarp");
        this.module = module;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase(SERVER_FLAG))) {
            throw fail("warp.setwarp-usage");
        }
        boolean server = args.length == 2;
        if (server) {
            requirePermission(player, SERVER_PERMISSION);
        }
        String name = args[0];
        UUID uuid = player.getUniqueId();
        WarpManager manager = module.manager();
        WarpManager.CreateResult result = manager.create(player, name, server, () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                messages().send(online, "general.database-error");
            }
        });

        double price = module.config().price();
        TagResolver warp = Placeholder.unparsed("warp", name);
        TagResolver priceTag = Placeholder.unparsed("price", plugin.vault().format(price));
        switch (result) {
            case CREATED -> {
                if (server) {
                    messages().send(player, "warp.created-server", warp);
                } else if (price > 0) {
                    messages().send(player, "warp.created-paid", warp, priceTag);
                } else {
                    messages().send(player, "warp.created", warp);
                }
            }
            case INVALID_NAME -> messages().send(player, "warp.invalid-name");
            case ALREADY_EXISTS -> messages().send(player, "warp.already-exists", warp);
            case LIMIT_REACHED -> messages().send(player, "warp.limit",
                    Placeholder.unparsed("limit", String.valueOf(manager.getLimit(player))));
            case DISABLED_WORLD -> messages().send(player, "warp.disabled-world");
            case SPECTATOR -> messages().send(player, "warp.spectator");
            case NOT_ENOUGH_MONEY -> messages().send(player, "warp.not-enough-money", priceTag);
            case ECONOMY_UNAVAILABLE -> messages().send(player, "warp.economy-unavailable");
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && sender.hasPermission(SERVER_PERMISSION)) {
            return filter(List.of(SERVER_FLAG), args[1]);
        }
        return List.of();
    }
}
