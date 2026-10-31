package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * /warpinfo &lt;name&gt; — owner, location, visits, rating and creation date. Usable from the console.
 */
final class WarpInfoCommand extends BaseCommand {

    static final String PERMISSION = "core.warpinfo";

    private final WarpModule module;

    WarpInfoCommand(WarpModule module) {
        super(module.plugin(), "warpinfo", PERMISSION, "Информация о варпе");
        this.module = module;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            throw fail("warp.warpinfo-usage");
        }
        Warp warp = module.manager().get(args[0]);
        if (warp == null) {
            throw fail("warp.not-found", Placeholder.unparsed("warp", args[0]));
        }

        UUID owner = warp.owner();
        if (warp.system() || owner == null || Bukkit.getPlayer(owner) != null) {
            send(sender, warp, WarpFormat.ownerName(module.config(), warp));
            return;
        }

        // Offline owner: the name may have changed since the warps were loaded.
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        CompletableFuture<Optional<String>> lookup = module.manager().findUserName(owner);
        lookup.whenCompleteAsync((name, error) -> {
            CommandSender target = senderId == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(senderId);
            if (target == null) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to look up the owner of warp '" + warp.name() + "'", error);
                messages().send(target, "general.database-error");
                return;
            }
            Warp current = module.manager().get(warp.name());
            Warp shown = current != null && current.sameAs(warp) ? current : warp;
            send(target, shown, name.orElseGet(() -> WarpFormat.ownerName(module.config(), shown)));
        }, plugin.mainThread());
    }

    private void send(CommandSender target, Warp warp, String ownerName) {
        messages().send(target, "warp.info",
                WarpFormat.placeholders(module.config(), warp, ownerName),
                Placeholder.component("type", messages().get(warp.system() ? "warp.type-server" : "warp.type-player")));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(module.manager().names(), args[0]);
        }
        return List.of();
    }
}
