package me.x1no4ekk.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * MiniMessage helpers shared by the plugin.
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Text() {
    }

    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }

    public static Component parse(String input, TagResolver... resolvers) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(input, resolvers);
    }

    /**
     * Parses text for item names/lore: italic is disabled unless the template explicitly enables it.
     */
    public static Component item(String input, TagResolver... resolvers) {
        return parse(input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLines(List<String> lines, TagResolver... resolvers) {
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(item(line, resolvers));
        }
        return result;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
