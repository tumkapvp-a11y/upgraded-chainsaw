package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.user.User;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Argument-less switch of a persistent {@link User} flag: /chattoggle, /msgtoggle, /socialspy.
 */
final class ToggleCommand extends BaseCommand {

    private final Predicate<User> getter;
    private final BiConsumer<User, Boolean> setter;
    private final String usageKey;
    private final String onKey;
    private final String offKey;

    ToggleCommand(CorePlugin plugin, String name, String permission, String description,
                  Predicate<User> getter, BiConsumer<User, Boolean> setter,
                  String usageKey, String onKey, String offKey) {
        super(plugin, name, permission, description);
        this.getter = getter;
        this.setter = setter;
        this.usageKey = usageKey;
        this.onKey = onKey;
        this.offKey = offKey;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 0) {
            throw fail(usageKey);
        }
        User user = requireUser(player);
        boolean enabled = !getter.test(user);
        setter.accept(user, enabled);
        messages().send(player, enabled ? onKey : offKey);
    }
}
