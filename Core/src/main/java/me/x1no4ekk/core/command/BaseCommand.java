package me.x1no4ekk.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.config.Messages;
import me.x1no4ekk.core.user.User;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Base for every Core command, registered through Paper's Brigadier lifecycle API (no plugin.yml entries).
 * <p>
 * Helpers throw {@link CommandFailure} to abort with a message, so command bodies stay linear.
 * Commands are stateless singletons: per-player state belongs in managers keyed by UUID.
 */
public abstract class BaseCommand implements BasicCommand {

    protected final CorePlugin plugin;
    private final String name;
    private final String permission;
    private final String description;
    private final List<String> aliases;

    /**
     * @param permission permission required to see and run the command, or {@code null} for everyone
     */
    protected BaseCommand(CorePlugin plugin, String name, @Nullable String permission, String description, String... aliases) {
        this.plugin = plugin;
        this.name = name.toLowerCase(Locale.ROOT);
        this.permission = permission;
        this.description = description;
        this.aliases = List.of(aliases);
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final List<String> aliases() {
        return aliases;
    }

    @Override
    public final @Nullable String permission() {
        return permission;
    }

    @Override
    public final void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        try {
            execute(sender, Arrays.stream(args).filter(arg -> !arg.isEmpty()).toArray(String[]::new));
        } catch (CommandFailure failure) {
            if (failure.key() != null) {
                messages().send(sender, failure.key(), failure.resolvers());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Error while executing /" + name + " for " + sender.getName(), e);
            messages().send(sender, "general.command-error");
        }
    }

    @Override
    public final @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        String[] normalized = args.length == 0 ? new String[]{""} : args;
        try {
            return complete(source.getSender(), normalized);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * @param args arguments without empty entries
     */
    protected abstract void execute(CommandSender sender, String[] args);

    /**
     * @param args arguments; the last element is the (possibly empty) word being typed
     */
    protected List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }

    // ---------------------------------------------------------------- helpers

    protected final Messages messages() {
        return plugin.messages();
    }

    protected static CommandFailure fail(String key, TagResolver... resolvers) {
        return new CommandFailure(key, resolvers);
    }

    /**
     * Abort without sending anything (the command already replied).
     */
    protected static CommandFailure stop() {
        return new CommandFailure(null);
    }

    protected final Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw fail("general.players-only");
    }

    protected final void requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            throw fail("general.no-permission");
        }
    }

    protected final User requireUser(Player player) {
        User user = plugin.userManager().getUser(player);
        if (user == null) {
            throw fail("general.data-not-loaded");
        }
        return user;
    }

    /**
     * Online player by exact (case-insensitive) name, hidden if vanished and the viewer cannot see them.
     */
    protected final @Nullable Player findPlayer(CommandSender viewer, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null || !plugin.vanishManager().canSee(viewer, player)) {
            return null;
        }
        return player;
    }

    protected final Player requireOnline(CommandSender viewer, String name) {
        Player player = findPlayer(viewer, name);
        if (player == null) {
            throw fail("general.player-not-found", Placeholder.unparsed("player", name));
        }
        return player;
    }

    /**
     * {@code args[index]} as a target (requires {@code othersPermission}) or the sender themself.
     */
    protected final Player targetOrSelf(CommandSender sender, String[] args, int index, String othersPermission) {
        if (args.length > index) {
            Player target = requireOnline(sender, args[index]);
            if (!target.equals(sender)) {
                requirePermission(sender, othersPermission);
            }
            return target;
        }
        return requirePlayer(sender);
    }

    protected final int parseInt(String raw, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw fail("general.invalid-number", Placeholder.unparsed("value", raw));
        }
        if (value < min || value > max) {
            throw fail("general.number-range",
                    Placeholder.unparsed("min", String.valueOf(min)),
                    Placeholder.unparsed("max", String.valueOf(max)));
        }
        return value;
    }

    protected final double parseDouble(String raw, double min, double max) {
        double value;
        try {
            value = Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw fail("general.invalid-number", Placeholder.unparsed("value", raw));
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw fail("general.number-range",
                    Placeholder.unparsed("min", formatNumber(min)),
                    Placeholder.unparsed("max", formatNumber(max)));
        }
        return value;
    }

    protected static String joinArgs(String[] args, int from) {
        if (from >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    /**
     * Names of online players visible to the viewer that start with the prefix.
     */
    protected final List<String> playerNames(CommandSender viewer, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.vanishManager().canSee(viewer, player) && player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    protected static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
