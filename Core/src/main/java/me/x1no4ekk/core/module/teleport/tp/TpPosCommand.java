package me.x1no4ekk.core.module.teleport.tp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.command.BaseCommand;
import me.x1no4ekk.core.teleport.TeleportOptions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /tppos &lt;x&gt; &lt;y&gt; &lt;z&gt; [yaw] [pitch] [world] — teleport to coordinates; {@code ~} and {@code ~n}
 * are relative to the current position. Whole absolute x/z are centered on the block like vanilla /tp.
 */
public final class TpPosCommand extends BaseCommand {

    public static final String PERMISSION = "core.tppos";

    private static final double MAX_HORIZONTAL = 30_000_000;
    private static final double MAX_VERTICAL = 20_000_000;
    private static final double MAX_ROTATION = 3_600;

    public TpPosCommand(CorePlugin plugin) {
        super(plugin, "tppos", PERMISSION, "Телепортация по координатам");
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 3 || args.length > 6) {
            throw fail("tp.tppos-usage");
        }
        teleportToPosition(player, args);
    }

    /**
     * Parses {@code x y z [yaw] [pitch] [world]} and teleports the player; also used by /tp with coordinates.
     * A non-numeric 4th or 5th argument is taken as the world name.
     */
    void teleportToPosition(Player player, String[] args) {
        Location base = player.getLocation();
        double x = coordinate(args[0], base.getX(), true, MAX_HORIZONTAL);
        double y = coordinate(args[1], base.getY(), false, MAX_VERTICAL);
        double z = coordinate(args[2], base.getZ(), true, MAX_HORIZONTAL);
        float yaw = base.getYaw();
        float pitch = base.getPitch();
        World world = base.getWorld();

        int index = 3;
        if (index < args.length && isNumber(args[index])) {
            yaw = Location.normalizeYaw((float) coordinate(args[index], base.getYaw(), false, MAX_ROTATION));
            index++;
        }
        if (index < args.length && isNumber(args[index])) {
            pitch = (float) coordinate(args[index], base.getPitch(), false, 90);
            index++;
        }
        if (index < args.length) {
            world = Bukkit.getWorld(args[index]);
            if (world == null) {
                throw fail("tp.world-not-found", Placeholder.unparsed("world", args[index]));
            }
            index++;
        }
        if (index < args.length) {
            throw fail("tp.tppos-usage");
        }

        Location destination = new Location(world, x, y, z, yaw, pitch);
        UUID uuid = player.getUniqueId();
        String worldName = world.getName();
        plugin.teleportService().teleport(player, destination, TeleportOptions.ADMIN).thenAcceptAsync(result -> {
            Player online = Bukkit.getPlayer(uuid);
            if (result == TeleportResult.SUCCESS && online != null) {
                messages().send(online, "tp.position",
                        Placeholder.unparsed("world", worldName),
                        Placeholder.unparsed("x", format(x)),
                        Placeholder.unparsed("y", format(y)),
                        Placeholder.unparsed("z", format(z)));
            }
        }, plugin.mainThread());
    }

    private double coordinate(String raw, double base, boolean centerWhole, double limit) {
        boolean relative = raw.startsWith("~");
        String number = relative ? raw.substring(1) : raw;
        double value;
        if (relative && number.isEmpty()) {
            value = base;
        } else {
            double parsed;
            try {
                parsed = Double.parseDouble(number.replace(',', '.'));
            } catch (NumberFormatException e) {
                throw fail("general.invalid-number", Placeholder.unparsed("value", raw));
            }
            if (!Double.isFinite(parsed)) {
                throw fail("general.invalid-number", Placeholder.unparsed("value", raw));
            }
            boolean whole = number.indexOf('.') < 0 && number.indexOf(',') < 0;
            value = relative ? base + parsed : (centerWhole && whole ? parsed + 0.5 : parsed);
        }
        if (Math.abs(value) > limit) {
            throw fail("general.number-range",
                    Placeholder.unparsed("min", String.valueOf((long) -limit)),
                    Placeholder.unparsed("max", String.valueOf((long) limit)));
        }
        return value;
    }

    static boolean isNumber(String raw) {
        if (raw.startsWith("~")) {
            return true;
        }
        try {
            Double.parseDouble(raw.replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 3) {
            return filter(List.of("~"), args[args.length - 1]);
        }
        String last = args[args.length - 1];
        List<String> options = new ArrayList<>();
        if (args.length <= 5) {
            options.add("~");
        }
        for (World world : Bukkit.getWorlds()) {
            options.add(world.getName());
        }
        return filter(options, last);
    }
}
