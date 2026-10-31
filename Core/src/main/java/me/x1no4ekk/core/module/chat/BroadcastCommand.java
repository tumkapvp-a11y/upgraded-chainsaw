package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.util.Sounds;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;

/**
 * Server-wide announcements configured in chat.yml broadcasts.&lt;id&gt;:
 * /broadcast (/bc), /adbroadcast, /buybroadcast, /sellbroadcast, /streambroadcast &lt;url&gt; [text], /videobroadcast &lt;url&gt; [text].
 */
final class BroadcastCommand extends BaseCommand {

    private static final int MAX_URL_LENGTH = 512;

    private final ChatModule module;
    private final String id;
    private final String usageKey;
    private final boolean link;

    BroadcastCommand(CorePlugin plugin, ChatModule module, String id, String usageKey, boolean link, String... aliases) {
        super(plugin, id.equals("broadcast") ? id : id + "broadcast", permission(module.config().broadcast(id)),
                "Объявление для всего сервера", aliases);
        this.module = module;
        this.id = id;
        this.usageKey = usageKey;
        this.link = link;
    }

    private static @Nullable String permission(ChatConfig.BroadcastSettings settings) {
        return settings.permission().isEmpty() ? null : settings.permission();
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        ChatConfig.BroadcastSettings settings = module.config().broadcast(id);
        if (!settings.permission().isEmpty()) {
            requirePermission(sender, settings.permission());
        }
        String url = null;
        String text;
        if (link) {
            if (args.length == 0) {
                throw fail(usageKey);
            }
            url = validateUrl(args[0]);
            text = joinArgs(args, 1);
        } else {
            if (args.length == 0) {
                throw fail(usageKey);
            }
            text = joinArgs(args, 0);
        }

        Player player = sender instanceof Player online ? online : null;
        User user = player == null ? null : requireUser(player);
        String cooldownKey = "broadcast:" + id;
        boolean cooldown = user != null && settings.cooldownMillis() > 0
                && !player.hasPermission(ChatPermissions.BROADCAST_BYPASS_COOLDOWN);
        if (cooldown) {
            long left = plugin.userManager().getCooldownRemaining(user, cooldownKey);
            if (left > 0) {
                throw fail("broadcast.cooldown", Placeholder.unparsed("time", Durations.formatMillis(left)));
            }
        }

        Component line = Text.parse(settings.format(),
                Placeholder.unparsed("name", module.senderName(sender)),
                Placeholder.component("message", message(sender, settings, text)),
                Placeholder.component("link", url == null ? Component.empty() : linkComponent(settings, url)));

        double price = settings.price();
        boolean paid = player != null && price > 0 && !player.hasPermission(ChatPermissions.BROADCAST_FREE);
        if (paid) {
            charge(player, price);
        }
        if (cooldown) {
            plugin.userManager().startCooldown(user, cooldownKey, settings.cooldownMillis());
        }

        UUID author = player == null ? null : player.getUniqueId();
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (settings.respectIgnore() && author != null && isIgnoring(recipient, author)) {
                continue;
            }
            recipient.sendMessage(line);
            Sounds.play(recipient, settings.sound());
        }
        Bukkit.getConsoleSender().sendMessage(line);
        if (paid) {
            messages().send(sender, "broadcast.paid", Placeholder.unparsed("price", plugin.vault().format(price)));
        }
    }

    /**
     * Staff broadcasts parse full MiniMessage; players need core.broadcast.format for safe formatting tags.
     */
    private static Component message(CommandSender sender, ChatConfig.BroadcastSettings settings, String text) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        if (settings.parseMiniMessage()) {
            return Text.parse(text);
        }
        if (sender.hasPermission(ChatPermissions.BROADCAST_FORMAT)) {
            return ChatFormatter.PLAYER_FORMATTING.deserialize(text);
        }
        return Component.text(text);
    }

    private static Component linkComponent(ChatConfig.BroadcastSettings settings, String url) {
        ClickEvent click = ClickEvent.openUrl(url);
        if (settings.linkMode() == ChatConfig.LinkMode.LABEL) {
            return Text.parse(settings.linkLabel()).clickEvent(click).hoverEvent(HoverEvent.showText(Component.text(url)));
        }
        return Component.text(url).clickEvent(click);
    }

    private String validateUrl(String raw) {
        TagResolver value = Placeholder.unparsed("link", raw);
        if (raw.length() > MAX_URL_LENGTH) {
            throw fail("broadcast.invalid-link", value);
        }
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw fail("broadcast.invalid-link", value);
            }
        } catch (URISyntaxException e) {
            throw fail("broadcast.invalid-link", value);
        }
        return raw;
    }

    /**
     * Withdraws the price before anything is sent. Main thread (Vault).
     */
    private void charge(Player player, double price) {
        TagResolver priceTag = Placeholder.unparsed("price", plugin.vault().format(price));
        if (!plugin.vault().hasEconomy()) {
            throw fail("broadcast.economy-unavailable");
        }
        if (!plugin.vault().has(player, price) || !plugin.vault().withdraw(player, price)) {
            throw fail("broadcast.not-enough-money", priceTag,
                    Placeholder.unparsed("balance", plugin.vault().format(plugin.vault().balance(player))));
        }
    }

    private boolean isIgnoring(Player recipient, UUID author) {
        if (recipient.getUniqueId().equals(author)) {
            return false;
        }
        User user = plugin.userManager().getUser(recipient);
        return user != null && user.isIgnoring(author);
    }
}
