package me.x1no4ekk.core.module.teleport.tpa;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.module.teleport.TeleportConfig;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Supplier;

/**
 * /tpa &lt;player&gt; — asks another player for permission to teleport to them.
 */
public final class TpaCommand extends BaseCommand {

    public static final String TOGGLE_BYPASS_PERMISSION = "core.tptoggle.bypass";

    private final TpaManager manager;
    private final Supplier<TeleportConfig> config;

    public TpaCommand(CorePlugin plugin, TpaManager manager, Supplier<TeleportConfig> config) {
        super(plugin, "tpa", "core.tpa", "Запрос на телепортацию к игроку");
        this.manager = manager;
        this.config = config;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length != 1) {
            throw fail("tpa.usage");
        }
        Player target = requireOnline(sender, args[0]);
        if (target.equals(player)) {
            throw fail("tpa.self");
        }
        User targetUser = plugin.userManager().getUser(target);
        if (targetUser == null) {
            throw fail("general.player-not-found", Placeholder.unparsed("player", args[0]));
        }
        TagResolver targetName = Placeholder.unparsed("player", target.getName());
        if (targetUser.isIgnoring(player.getUniqueId())) {
            throw fail("tpa.ignored", targetName);
        }
        if (!targetUser.isTpToggle() && !player.hasPermission(TOGGLE_BYPASS_PERMISSION)) {
            throw fail("tpa.disabled", targetName);
        }
        if (manager.hasRequest(target.getUniqueId(), player.getUniqueId())) {
            throw fail("tpa.already-sent", targetName);
        }
        long cooldown = manager.cooldownRemaining(player.getUniqueId());
        if (cooldown > 0) {
            throw fail("tpa.cooldown", Placeholder.unparsed("time", Durations.formatMillis(cooldown)));
        }

        TeleportConfig.Tpa settings = config.get().tpa();
        manager.add(target.getUniqueId(), player.getUniqueId());
        TagResolver seconds = Placeholder.unparsed("seconds", String.valueOf(settings.expireMillis() / 1000));
        messages().send(player, "tpa.sent", targetName, seconds);

        String senderName = player.getName();
        TagResolver senderPlaceholder = Placeholder.unparsed("player", senderName);
        messages().send(target, "tpa.received", senderPlaceholder, seconds,
                Placeholder.component("accept", button("tpa.button-accept", "/tpaccept " + senderName, senderPlaceholder)),
                Placeholder.component("deny", button("tpa.button-deny", "/tpdeny " + senderName, senderPlaceholder)));
        Sounds.play(target, settings.soundKey(), settings.soundVolume(), settings.soundPitch());
    }

    /**
     * The click command is attached in code rather than via a MiniMessage click tag, so the player name
     * is never parsed as markup.
     */
    private Component button(String key, String command, TagResolver player) {
        return messages().get(key, player)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(messages().get(key + "-hover", player)));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = playerNames(sender, args[0]);
        names.remove(sender.getName());
        return names;
    }
}
