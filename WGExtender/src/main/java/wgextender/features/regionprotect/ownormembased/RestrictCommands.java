package wgextender.features.regionprotect.ownormembased;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import wgextender.Config;
import wgextender.utils.CommandUtils;
import wgextender.utils.WGRegionUtils;

/**
 * Blocks configured commands for players standing in a region where they can't build.
 * Aliases are resolved on the fly through the command map, no background task is needed.
 */
public final class RestrictCommands implements Listener {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Component DENY_MESSAGE = Component.text("Вы не можете использовать эту команду на чужом регионе", NamedTextColor.RED);

    private final Config config;

    public RestrictCommands(Config config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        List<RestrictedCommand> restricted = config.restrictedCommandsInRegion;
        if (!config.restrictCommandsInRegionEnabled || restricted.isEmpty()) {
            return;
        }
        String[] input = WHITESPACE.split(event.getMessage().substring(1).trim().toLowerCase(Locale.ROOT));
        if (input.length == 0 || input[0].isEmpty() || !isRestricted(input, restricted)) {
            return;
        }

        // Cheap string matching first, region lookups only for restricted commands.
        Player player = event.getPlayer();
        ApplicableRegionSet regions = WGRegionUtils.getRegionsAt(player.getLocation());
        if (regions.size() == 0) {
            return;
        }
        LocalPlayer localPlayer = WGRegionUtils.wrapPlayer(player);
        if (!WGRegionUtils.canBuild(regions, localPlayer) && !WGRegionUtils.canBypassProtection(player, localPlayer)) {
            event.setCancelled(true);
            player.sendMessage(DENY_MESSAGE);
        }
    }

    private static boolean isRestricted(String[] input, List<RestrictedCommand> restricted) {
        String label = input[0];
        String plainLabel = stripNamespace(label);
        Command command = null;
        boolean commandResolved = false;
        for (RestrictedCommand entry : restricted) {
            if (!entry.argumentsMatch(input)) {
                continue;
            }
            if (entry.label().equals(plainLabel)) {
                return true;
            }
            if (!commandResolved) {
                command = CommandUtils.getCommand(label);
                commandResolved = true;
            }
            // Same command object means an alias, e.g. /ehome for /home.
            if (command != null && command == CommandUtils.getCommand(entry.label())) {
                return true;
            }
        }
        return false;
    }

    private static String stripNamespace(String label) {
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    /**
     * Entry of restrictcommands.commands, e.g. {@code home} or {@code warp spawn}.
     */
    public record RestrictedCommand(String label, String[] arguments) {

        public static RestrictedCommand parse(String entry) {
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (normalized.isEmpty()) {
                return null;
            }
            String[] parts = WHITESPACE.split(normalized);
            String[] arguments = new String[parts.length - 1];
            System.arraycopy(parts, 1, arguments, 0, arguments.length);
            return new RestrictedCommand(stripNamespace(parts[0]), arguments);
        }

        boolean argumentsMatch(String[] input) {
            if (input.length - 1 < arguments.length) {
                return false;
            }
            for (int i = 0; i < arguments.length; i++) {
                if (!arguments[i].equals(input[i + 1])) {
                    return false;
                }
            }
            return true;
        }
    }
}
