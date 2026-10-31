package me.x1no4ekk.core.hook;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI integration. In MiniMessage templates use {@code <papi:placeholder_name>} (without '%'):
 * the result (often legacy-colored) is converted to a component.
 */
public final class PlaceholderHook {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.AMPERSAND_CHAR).hexColors().build();

    private final boolean enabled;

    public PlaceholderHook() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Replaces %placeholders% in raw text; returns the input when PlaceholderAPI is absent.
     */
    public String apply(OfflinePlayer player, String text) {
        if (!enabled || text == null || text.indexOf('%') < 0) {
            return text;
        }
        return PapiBridge.apply(player, text);
    }

    /**
     * {@code <papi:name>} tag for MiniMessage; resolves to empty text when PlaceholderAPI is absent.
     */
    public TagResolver tag(OfflinePlayer player) {
        return TagResolver.resolver("papi", (args, context) -> {
            String name = args.popOr("<papi> requires a placeholder name").value();
            if (!enabled) {
                return Tag.selfClosingInserting(Component.empty());
            }
            return Tag.selfClosingInserting(legacy(PapiBridge.apply(player, "%" + name + "%")));
        });
    }

    /**
     * Converts legacy-colored text (§ or &) to a component.
     */
    public static Component legacy(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return text.indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0
                ? SECTION.deserialize(text)
                : AMPERSAND.deserialize(text);
    }
}
