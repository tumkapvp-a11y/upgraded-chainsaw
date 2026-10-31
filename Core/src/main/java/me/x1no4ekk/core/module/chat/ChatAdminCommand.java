package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /chat &lt;clear|mute|unmute&gt;
 */
final class ChatAdminCommand extends BaseCommand {

    private static final List<String> ACTIONS = List.of("clear", "mute", "unmute");

    private final ChatModule module;

    ChatAdminCommand(CorePlugin plugin, ChatModule module) {
        super(plugin, "chat", ChatPermissions.ADMIN, "Управление чатом: очистка и отключение");
        this.module = module;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            throw fail("chat.admin-usage");
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "clear" -> clear(sender);
            case "mute" -> {
                if (module.isMuted()) {
                    throw fail("chat.already-muted");
                }
                module.setMuted(true);
                announce("chat.muted-broadcast", sender);
            }
            case "unmute" -> {
                if (!module.isMuted()) {
                    throw fail("chat.not-muted");
                }
                module.setMuted(false);
                announce("chat.unmuted-broadcast", sender);
            }
            default -> throw fail("chat.admin-usage");
        }
    }

    private void clear(CommandSender sender) {
        Component blank = Component.text("\n".repeat(module.config().clearLines()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(ChatPermissions.BYPASS_CLEAR)) {
                player.sendMessage(blank);
            }
        }
        announce("chat.cleared", sender);
    }

    private void announce(String key, CommandSender sender) {
        String name = module.senderName(sender);
        for (Player player : Bukkit.getOnlinePlayers()) {
            messages().send(player, key, Placeholder.unparsed("player", name));
        }
        messages().send(Bukkit.getConsoleSender(), key, Placeholder.unparsed("player", name));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? filter(ACTIONS, args[0]) : List.of();
    }
}
