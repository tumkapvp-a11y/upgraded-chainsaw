package dev.applesub.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.applesub.AppleSubPlugin;
import dev.applesub.util.DurationUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public final class CommandRegistrar {

    private static final String ADMIN_PERMISSION = "applesub.admin";
    private static final List<String> DURATIONS = List.of("1d", "7d", "30d", "90d", "365d", "forever");

    private CommandRegistrar() {
    }

    public static void register(AppleSubPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(admin(plugin), "Управление подписками AppleSub", List.of("asub"));
            commands.register(sub(plugin), "Меню подписки AppleSub");
        });
    }

    private static LiteralCommandNode<CommandSourceStack> sub(AppleSubPlugin plugin) {
        return Commands.literal("sub")
                .executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                        plugin.settings().send(ctx.getSource().getSender(), "only-players");
                        return 0;
                    }
                    plugin.service().openMenu(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> admin(AppleSubPlugin plugin) {
        return Commands.literal("applesub")
                .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                .executes(ctx -> {
                    plugin.settings().sendLines(ctx.getSource().getSender(), "help");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(CommandRegistrar::suggestPlayers)
                                .executes(ctx -> give(plugin, ctx, null))
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .suggests(CommandRegistrar::suggestDurations)
                                        .executes(ctx -> give(plugin, ctx, StringArgumentType.getString(ctx, "duration"))))))
                .then(Commands.literal("take")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(CommandRegistrar::suggestPlayers)
                                .executes(ctx -> {
                                    plugin.service().take(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("check")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(CommandRegistrar::suggestPlayers)
                                .executes(ctx -> {
                                    plugin.service().check(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            plugin.settings().load();
                            plugin.settings().send(ctx.getSource().getSender(), "reload");
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static int give(AppleSubPlugin plugin, CommandContext<CommandSourceStack> ctx, String rawDuration) {
        CommandSender sender = ctx.getSource().getSender();
        long duration;
        if (rawDuration == null) {
            duration = plugin.settings().defaultDuration();
        } else {
            OptionalLong parsed = DurationUtil.parse(rawDuration);
            if (parsed.isEmpty()) {
                plugin.settings().send(sender, "invalid-duration", Placeholder.unparsed("input", rawDuration));
                return 0;
            }
            duration = parsed.getAsLong();
        }
        plugin.service().give(sender, StringArgumentType.getString(ctx, "player"), duration);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx,
                                                                 SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestDurations(CommandContext<CommandSourceStack> ctx,
                                                                   SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String duration : DURATIONS) {
            if (duration.startsWith(remaining)) {
                builder.suggest(duration);
            }
        }
        return builder.buildFuture();
    }
}
