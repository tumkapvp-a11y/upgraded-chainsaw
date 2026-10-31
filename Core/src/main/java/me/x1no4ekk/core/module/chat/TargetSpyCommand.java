package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /targetspy &lt;player|off&gt; — watch one player's private messages and commands.
 */
final class TargetSpyCommand extends BaseCommand {

    private final PrivateMessageService service;

    TargetSpyCommand(CorePlugin plugin, PrivateMessageService service) {
        super(plugin, "targetspy", ChatPermissions.TARGET_SPY, "Слежка за сообщениями и командами игрока");
        this.service = service;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player spy = requirePlayer(sender);
        if (args.length != 1) {
            throw fail("spy.target-usage");
        }
        UUID spyId = spy.getUniqueId();
        if (args[0].equalsIgnoreCase("off")) {
            UUID previous = service.stopSpying(spyId);
            if (previous == null) {
                throw fail("spy.target-none");
            }
            sendStopped(spy, previous);
            return;
        }
        Player target = requireOnline(spy, args[0]);
        if (target.getUniqueId().equals(spyId)) {
            throw fail("spy.target-self");
        }
        if (target.getUniqueId().equals(service.spyTarget(spyId))) {
            service.stopSpying(spyId);
            sendStopped(spy, target.getUniqueId());
            return;
        }
        service.startSpying(spyId, target.getUniqueId());
        messages().send(spy, "spy.target-on", Placeholder.unparsed("player", target.getName()));
    }

    private void sendStopped(Player spy, UUID target) {
        Player online = Bukkit.getPlayer(target);
        String name = online != null ? online.getName() : target.toString();
        messages().send(spy, "spy.target-off", Placeholder.unparsed("player", name));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(filter(List.of("off"), args[0]));
        options.addAll(playerNames(sender, args[0]));
        return options;
    }
}
