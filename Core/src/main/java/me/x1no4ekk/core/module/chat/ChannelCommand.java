package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.CooldownTracker;
import me.x1no4ekk.core.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /donatechat (/dc) and /staffchat (/sc): permission-based channels, recipients need the same permission.
 */
final class ChannelCommand extends BaseCommand {

    enum Kind {
        DONATE("donatechat", "dc", ChatPermissions.DONATE, "Донат-чат", "chat.donate-usage"),
        STAFF("staffchat", "sc", ChatPermissions.STAFF, "Чат персонала", "chat.staff-usage");

        private final String label;
        private final String alias;
        private final String permission;
        private final String description;
        private final String usageKey;

        Kind(String label, String alias, String permission, String description, String usageKey) {
            this.label = label;
            this.alias = alias;
            this.permission = permission;
            this.description = description;
            this.usageKey = usageKey;
        }
    }

    private final ChatModule module;
    private final Kind kind;

    ChannelCommand(CorePlugin plugin, ChatModule module, Kind kind) {
        super(plugin, kind.label, kind.permission, kind.description, kind.alias);
        this.module = module;
        this.kind = kind;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            throw fail(kind.usageKey);
        }
        Component text = Component.text(joinArgs(args, 0));
        ChatConfig config = module.config();
        ChatConfig.Channel channel = kind == Kind.DONATE ? config.donate() : config.staff();

        ChatFormatter.Sender author;
        UUID authorId = null;
        if (sender instanceof Player player) {
            authorId = player.getUniqueId();
            if (kind == Kind.DONATE && module.isMuted() && !player.hasPermission(ChatPermissions.BYPASS_MUTE)) {
                throw fail("chat.muted");
            }
            if (!player.hasPermission(ChatPermissions.BYPASS_COOLDOWN)) {
                CooldownTracker cooldown = kind == Kind.DONATE ? module.donateCooldown() : module.staffCooldown();
                long left = cooldown.remaining(authorId);
                if (left > 0) {
                    throw fail("chat.cooldown", Placeholder.unparsed("time", Durations.formatMillis(left)));
                }
                cooldown.start(authorId, channel.cooldownMillis());
            }
            author = module.formatter().player(player, module.states().refresh(player), config);
        } else {
            author = module.formatter().console(module.senderName(sender), config);
        }

        Component line = module.formatter().render(channel.format(), author, text);
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.hasPermission(kind.permission) && accepts(recipient, authorId)) {
                recipient.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    /**
     * Donate chat honours /chattoggle and /ignore; the staff channel always delivers.
     */
    private boolean accepts(Player recipient, UUID authorId) {
        if (kind == Kind.STAFF || authorId == null || recipient.getUniqueId().equals(authorId)) {
            return true;
        }
        User user = plugin.userManager().getUser(recipient);
        return user == null || (user.isChatToggle() && !user.isIgnoring(authorId));
    }
}
