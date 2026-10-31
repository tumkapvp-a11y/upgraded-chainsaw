package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.CoreApi;
import me.x1no4ekk.core.api.GuildTagProvider;
import me.x1no4ekk.core.hook.PlaceholderHook;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Builds chat lines from chat.yml formats. Thread-safe: used by the async chat thread and by commands.
 */
final class ChatFormatter {

    /**
     * Parser for player-written text: colors, decorations, gradients and rainbows only (no click/hover/insertion).
     */
    static final MiniMessage PLAYER_FORMATTING = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow()))
            .build();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final TagResolver NO_PLACEHOLDERS =
            TagResolver.resolver("papi", (arguments, context) -> Tag.selfClosingInserting(Component.empty()));

    /**
     * Everything about the author that a format needs, computed once per message (never per viewer).
     */
    record Sender(Component prefix, Component suffix, Component name, Component guild, String world,
                  TagResolver placeholders, String messageColor, boolean allowFormatting) {
    }

    private final CorePlugin plugin;

    ChatFormatter(CorePlugin plugin) {
        this.plugin = plugin;
    }

    Sender player(Player player, @Nullable PlayerStates.Snapshot snapshot, ChatConfig config) {
        ChatConfig.Group group = group(player, snapshot, config);
        ChatConfig.Group style = group != null ? group : config.defaultGroup();
        TagResolver papi = plugin.placeholders().tag(player);
        String world = snapshot != null ? snapshot.worldName() : "";

        Component prefix = Text.parse(style.prefix(), papi);
        Component suffix = Text.parse(style.suffix(), papi);
        if (group == null && config.vaultFallback() && snapshot != null) {
            if (snapshot.vaultPrefix() != null && !snapshot.vaultPrefix().isEmpty()) {
                prefix = PlaceholderHook.legacy(snapshot.vaultPrefix());
            }
            if (snapshot.vaultSuffix() != null && !snapshot.vaultSuffix().isEmpty()) {
                suffix = PlaceholderHook.legacy(snapshot.vaultSuffix());
            }
        }

        Component displayName = player.displayName();
        Component name = Text.parse(style.nameColor() + "<display-name>", Placeholder.component("display-name", displayName));
        ChatConfig.NameSettings settings = config.name();
        if (!settings.hover().isEmpty()) {
            Component hover = Text.parse(settings.hover(),
                    Placeholder.unparsed("name", player.getName()),
                    Placeholder.component("display-name", displayName),
                    Placeholder.unparsed("world", world),
                    papi);
            name = name.hoverEvent(HoverEvent.showText(hover));
        }
        ClickEvent click = settings.click(player.getName());
        if (click != null) {
            name = name.clickEvent(click);
        }

        return new Sender(prefix, suffix, name, guild(player.getUniqueId(), config), world, papi,
                style.messageColor(), style.allowFormatting());
    }

    /**
     * Console author for donate/staff chat.
     */
    Sender console(String name, ChatConfig config) {
        ChatConfig.Group style = config.defaultGroup();
        Component displayName = Text.parse(style.nameColor() + "<display-name>", Placeholder.unparsed("display-name", name));
        return new Sender(Component.empty(), Component.empty(), displayName, Component.empty(), "", NO_PLACEHOLDERS,
                style.messageColor(), true);
    }

    /**
     * Renders a chat format. Tags: prefix, suffix, name, guild, message, world and papi.
     */
    Component render(String format, Sender sender, Component message) {
        return Text.parse(format,
                Placeholder.component("prefix", sender.prefix()),
                Placeholder.component("suffix", sender.suffix()),
                Placeholder.component("name", sender.name()),
                Placeholder.component("guild", sender.guild()),
                Placeholder.unparsed("world", sender.world()),
                Placeholder.component("message", body(sender, message)),
                sender.placeholders());
    }

    /**
     * Player text with the privilege's message color; formatting tags are honoured only when the privilege allows them.
     */
    private static Component body(Sender sender, Component message) {
        String plain = PLAIN.serialize(message);
        TagResolver text = sender.allowFormatting()
                ? Placeholder.component("text", PLAYER_FORMATTING.deserialize(plain))
                : Placeholder.unparsed("text", plain);
        return Text.parse(sender.messageColor() + "<text>", text);
    }

    private static ChatConfig.@Nullable Group group(Player player, @Nullable PlayerStates.Snapshot snapshot, ChatConfig config) {
        String vaultGroup = snapshot == null || snapshot.vaultGroup() == null
                ? null
                : snapshot.vaultGroup().toLowerCase(Locale.ROOT);
        for (ChatConfig.Group group : config.groups()) {
            if (!group.permission().isEmpty() && player.hasPermission(group.permission())) {
                return group;
            }
            if (vaultGroup != null && group.vaultGroups().contains(vaultGroup)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Guild tag resolved per message: the provider may be registered or replaced at any time.
     */
    private Component guild(UUID uuid, ChatConfig config) {
        CoreApi api = plugin.api();
        GuildTagProvider provider = api == null ? null : api.getGuildTagProvider();
        if (provider == null) {
            return Component.empty();
        }
        Component tag;
        try {
            tag = provider.guildTag(uuid);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Guild tag provider failed for " + uuid, e);
            return Component.empty();
        }
        return tag == null ? Component.empty() : Text.parse(config.guildFormat(), Placeholder.component("tag", tag));
    }
}
