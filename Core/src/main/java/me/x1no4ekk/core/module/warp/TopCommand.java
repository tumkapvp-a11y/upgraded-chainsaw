package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /baltop, /killtop, /deathtop — print the cached top; never queries MySQL.
 */
final class TopCommand extends BaseCommand {

    private final WarpModule module;
    private final TopCache.Type type;

    TopCommand(WarpModule module, TopCache.Type type, String name, String permission, String description, String... aliases) {
        super(module.plugin(), name, permission, description, aliases);
        this.module = module;
        this.type = type;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            throw fail("tops.usage", Placeholder.unparsed("command", name()));
        }
        TopCache.Snapshot snapshot = module.tops().get(type);
        if (snapshot == null) {
            throw fail("tops.not-ready");
        }
        if (snapshot.entries().isEmpty()) {
            throw fail("tops.empty");
        }

        String section = "tops." + type.messageSection();
        TagResolver updated = Placeholder.unparsed("updated",
                Durations.format((System.currentTimeMillis() - snapshot.updatedAt()) / 1000));
        List<Component> lines = new ArrayList<>();
        addLine(lines, messages().get(section + ".header", updated));
        List<TopCache.Entry> entries = snapshot.entries();
        for (int i = 0; i < entries.size(); i++) {
            TopCache.Entry entry = entries.get(i);
            TagResolver position = Placeholder.unparsed("position", String.valueOf(i + 1));
            addLine(lines, messages().get(section + ".line",
                    position,
                    Placeholder.component("place", messages().get(placeKey(i), position)),
                    Placeholder.unparsed("name", entry.name()),
                    Placeholder.unparsed("value", formatValue(entry.value()))));
        }
        addLine(lines, messages().get(section + ".footer", updated));
        sender.sendMessage(Component.join(JoinConfiguration.newlines(), lines));
    }

    private String formatValue(double value) {
        // VaultHook.format falls back to a plain number when no economy is registered.
        return type == TopCache.Type.BALANCE ? plugin.vault().format(value) : String.valueOf((long) value);
    }

    private static String placeKey(int index) {
        return switch (index) {
            case 0 -> "tops.place.first";
            case 1 -> "tops.place.second";
            case 2 -> "tops.place.third";
            default -> "tops.place.other";
        };
    }

    private static void addLine(List<Component> lines, Component line) {
        if (!Component.empty().equals(line)) {
            lines.add(line);
        }
    }
}
