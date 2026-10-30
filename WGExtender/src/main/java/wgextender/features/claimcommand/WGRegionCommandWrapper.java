package wgextender.features.claimcommand;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import wgextender.Config;
import wgextender.WGExtender;
import wgextender.utils.CommandUtils;
import wgextender.utils.WEUtils;
import wgextender.utils.WGRegionUtils;

/**
 * Wraps WorldGuard's /region command and takes over "/region claim". Everything else,
 * including tab completion and permission checks, goes to the original command.
 */
public final class WGRegionCommandWrapper {

    private final Config config;
    private Command originalCmd;
    private Wrapper wrapper;

    public WGRegionCommandWrapper(Config config) {
        this.config = config;
    }

    public void inject() {
        if (wrapper != null) {
            return;
        }
        Command original = CommandUtils.getCommand("region");
        if (original == null) {
            throw new IllegalStateException("WorldGuard /region command is not registered");
        }
        Wrapper newWrapper = new Wrapper(original);
        CommandUtils.replaceCommand(original, newWrapper);
        originalCmd = original;
        wrapper = newWrapper;
    }

    public void uninject() {
        if (wrapper == null) {
            return;
        }
        CommandUtils.replaceCommand(wrapper, originalCmd);
        wrapper = null;
        originalCmd = null;
    }

    private boolean claim(Player player, String regionName) {
        if (config.claimExpandSelectionVertical && WEUtils.expandVert(player)) {
            player.sendMessage(Component.text("Регион автоматически расширен по вертикали", NamedTextColor.YELLOW));
        }
        if (!checkBlockLimits(player)) {
            return true;
        }
        try {
            ProtectedRegion region = WGClaimCommand.claim(regionName, player);
            if (config.claimAutoFlagsEnabled && !config.claimAutoFlags.isEmpty()) {
                AutoFlags.setFlagsForRegion(
                    WGRegionUtils.wrapAsPrivileged(player, true), player.getWorld(), region,
                    config.claimAutoFlags, WGExtender.getInstance().getLogger()
                );
            }
        } catch (CommandException e) {
            String message = e.getMessage() != null ? e.getMessage() : "Недостаточно прав";
            player.sendMessage(Component.text(message, NamedTextColor.RED));
        }
        return true;
    }

    private boolean checkBlockLimits(Player player) {
        BlockLimits.ProcessedClaimInfo info = BlockLimits.processClaimInfo(config, player);
        String title;
        String details;
        switch (info.result()) {
            case DENY_MAX_VOLUME -> {
                title = "Вы не можете создать такой большой регион";
                details = "Максимальный размер: " + info.assignedLimit() + ", размер вашего выделения: " + info.assignedSize();
            }
            case DENY_MIN_VOLUME -> {
                title = "Вы не можете создать регион такого размера";
                details = "Минимальный объем: " + info.assignedLimit() + ", вы попытались создать: " + info.assignedSize();
            }
            case DENY_HORIZONTAL -> {
                title = "Вы не можете создать регион такого размера";
                details = "Минимальная ширина: " + info.assignedLimit() + ", вы попытались создать: " + info.assignedSize();
            }
            case DENY_VERTICAL -> {
                title = "Вы не можете создать такой низкий регион";
                details = "Минимальная высота: " + info.assignedLimit() + ", вы попытались создать: " + info.assignedSize();
            }
            default -> {
                return true;
            }
        }
        player.sendMessage(Component.text(title, NamedTextColor.RED));
        player.sendMessage(Component.text(details, NamedTextColor.RED));
        return false;
    }

    private final class Wrapper extends Command {
        private final Command original;

        private Wrapper(Command original) {
            super(original.getName(), original.getDescription(), original.getUsage(), original.getAliases());
            this.original = original;
            setPermission(original.getPermission());
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (sender instanceof Player player && args.length >= 2 && args[0].equalsIgnoreCase("claim")) {
                return claim(player, args[1]);
            }
            return original.execute(sender, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return original.tabComplete(sender, alias, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
            return original.tabComplete(sender, alias, args, location);
        }

        @Override
        public boolean testPermissionSilent(CommandSender target) {
            return original.testPermissionSilent(target);
        }

        @Override
        public boolean testPermission(CommandSender target) {
            return original.testPermission(target);
        }
    }
}
