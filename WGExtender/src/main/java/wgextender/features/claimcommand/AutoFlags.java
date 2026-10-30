package wgextender.features.claimcommand;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.commands.region.RegionCommands;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.World;

/**
 * Sets flags through WorldGuard's own /region flag implementation, so every value syntax
 * (including -g group and -e) works exactly like in the command.
 */
// WorldGuard's /region commands are still built on the legacy command framework
@SuppressWarnings("deprecation")
public final class AutoFlags {

    private static RegionCommands regionCommands;
    private static Set<Character> flagCommandValueFlags;

    private AutoFlags() {
    }

    static void setFlagsForRegion(Actor actor, World world, ProtectedRegion region, Map<Flag<?>, String> flags, Logger logger) {
        for (Map.Entry<Flag<?>, String> entry : flags.entrySet()) {
            try {
                setFlag(actor, world, region, entry.getKey(), entry.getValue());
            } catch (CommandException e) {
                logger.warning("autoflags: unable to set " + entry.getKey().getName() + " on " + region.getId() + ": " + e.getMessage());
            }
        }
    }

    public static void setFlag(Actor actor, World world, ProtectedRegion region, Flag<?> flag, String value) throws CommandException {
        if (regionCommands == null) {
            regionCommands = new RegionCommands(WorldGuard.getInstance());
            flagCommandValueFlags = getFlagCommandValueFlags();
        }
        String command = "flag " + region.getId() + " -w " + world.getName() + " " + flag.getName();
        if (value != null && !value.isEmpty()) {
            command += " " + value;
        }
        regionCommands.flag(new CommandContext(command, flagCommandValueFlags), actor);
    }

    /**
     * Value flags of the /region flag command (e.g. "w" and "g"), read from its annotation.
     */
    private static Set<Character> getFlagCommandValueFlags() {
        Set<Character> valueFlags = new HashSet<>();
        try {
            Command annotation = RegionCommands.class.getMethod("flag", CommandContext.class, Actor.class).getAnnotation(Command.class);
            char[] chars = annotation.flags().toCharArray();
            for (int i = 0; i < chars.length - 1; i++) {
                if (chars[i + 1] == ':') {
                    valueFlags.add(chars[i]);
                    i++;
                }
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Unsupported WorldGuard version", e);
        }
        return valueFlags;
    }
}
