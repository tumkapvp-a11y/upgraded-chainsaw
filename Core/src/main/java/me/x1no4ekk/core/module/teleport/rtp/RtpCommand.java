package me.x1no4ekk.core.module.teleport.rtp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.module.teleport.TeleportConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * /rtp [option] — opens the random teleport menu, or starts the given option directly.
 */
public final class RtpCommand extends BaseCommand {

    private final RtpService service;
    private final Supplier<TeleportConfig> config;

    public RtpCommand(CorePlugin plugin, RtpService service, Supplier<TeleportConfig> config) {
        super(plugin, "rtp", "core.rtp", "Случайная телепортация");
        this.service = service;
        this.config = config;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length > 1) {
            throw fail("rtp.usage");
        }
        if (args.length == 0) {
            new RtpGui(plugin, service, config, player).open(player);
            return;
        }
        TeleportConfig.RtpOption option = config.get().rtp().options().get(args[0].toLowerCase(Locale.ROOT));
        if (option == null) {
            throw fail("rtp.unknown-option",
                    Placeholder.unparsed("option", args[0]),
                    Placeholder.unparsed("options", String.join(", ", available(player))));
        }
        service.start(player, option);
    }

    private List<String> available(Player player) {
        List<String> ids = new ArrayList<>();
        for (TeleportConfig.RtpOption option : config.get().rtp().options().values()) {
            if (service.hasAccess(player, option)) {
                ids.add(option.id());
            }
        }
        return ids;
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        return filter(available(player), args[0]);
    }
}
