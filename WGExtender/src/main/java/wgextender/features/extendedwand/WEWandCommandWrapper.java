package wgextender.features.extendedwand;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import wgextender.Config;
import wgextender.utils.CommandUtils;

/**
 * Replaces WorldEdit's //wand with a wand that can't hurt entities and disappears when dropped.
 * Injected only while extendedwewand is enabled.
 */
public final class WEWandCommandWrapper {

    private static final Component GIVEN_MESSAGE = Component.text("Выдана вещь для выделения территории", NamedTextColor.LIGHT_PURPLE);

    private final Config config;
    private Command originalCmd;
    private Wrapper wrapper;

    public WEWandCommandWrapper(Config config) {
        this.config = config;
    }

    public void inject() {
        if (wrapper != null) {
            return;
        }
        Command original = CommandUtils.getCommand("/wand");
        if (original == null) {
            throw new IllegalStateException("WorldEdit //wand command is not registered");
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

    private final class Wrapper extends Command {
        private final Command original;

        private Wrapper(Command original) {
            super(original.getName(), original.getDescription(), original.getUsage(), original.getAliases());
            this.original = original;
            setPermission(original.getPermission());
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!config.extendedWorldEditWandEnabled) {
                return original.execute(sender, label, args);
            }
            if (!original.testPermission(sender)) {
                return true;
            }
            if (sender instanceof Player player) {
                player.getInventory().addItem(WEWand.getWand());
                player.sendMessage(GIVEN_MESSAGE);
            }
            return true;
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
