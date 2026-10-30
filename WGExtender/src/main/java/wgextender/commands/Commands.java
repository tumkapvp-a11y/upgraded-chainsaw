package wgextender.commands;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import wgextender.WGExtender;
import wgextender.features.claimcommand.AutoFlags;
import wgextender.utils.WEUtils;
import wgextender.utils.WGRegionUtils;

public final class Commands implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "wgextender.admin";
    private static final List<String> PLAYER_SUBCOMMANDS = List.of("help", "reload", "search", "setflag", "removeowner", "removemember");
    private static final List<String> CONSOLE_SUBCOMMANDS = List.of("help", "reload", "setflag", "removeowner", "removemember");

    private final WGExtender plugin;

    public Commands(WGExtender plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("Недостаточно прав", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                info(sender, "wgex reload - перезагрузить конфиг");
                info(sender, "wgex search - ищет регионы в выделенной области");
                info(sender, "wgex setflag {world} {flag} {value} - устанавливает флаг {flag} со значением {value} на все регионы в мире {world}");
                info(sender, "wgex removeowner {name} - удаляет игрока из списков владельцев всех регионов");
                info(sender, "wgex removemember {name} - удаляет игрока из списков членов всех регионов");
                return true;
            }
            case "reload" -> {
                plugin.reloadSettings();
                info(sender, "Конфиг перезагружен");
                return true;
            }
            case "search" -> {
                if (!(sender instanceof Player player)) {
                    return false;
                }
                search(player);
                return true;
            }
            case "setflag" -> {
                if (args.length < 4) {
                    return false;
                }
                setFlag(sender, args[1], args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                return true;
            }
            case "removeowner", "removemember" -> {
                if (args.length != 2) {
                    return false;
                }
                removeFromAllRegions(sender, args[1], args[0].equalsIgnoreCase("removeowner"));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void info(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.BLUE));
    }

    private static void search(Player player) {
        RegionManager manager = WGRegionUtils.getRegionManager(player.getWorld());
        if (manager == null) {
            info(player, "Регионы отключены в этом мире");
            return;
        }
        Region selection;
        try {
            selection = WEUtils.getSelection(player);
        } catch (IncompleteRegionException e) {
            info(player, "Сначала выделите зону поиска");
            return;
        }
        ProtectedRegion area = new ProtectedCuboidRegion("wgextender-search", true, selection.getMinimumPoint(), selection.getMaximumPoint());
        List<String> regions = new ArrayList<>();
        for (ProtectedRegion region : manager.getApplicableRegions(area)) {
            regions.add(region.getId());
        }
        if (regions.isEmpty()) {
            info(player, "Регионов пересекающихся с выделенной зоной не найдено");
        } else {
            info(player, "Найдены регионы пересекающиеся с выделенной зоной: " + regions);
        }
    }

    private static void setFlag(CommandSender sender, String worldName, String flagName, String value) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            info(sender, "Мир не найден");
            return;
        }
        Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), flagName);
        if (flag == null) {
            info(sender, "Флаг не найден");
            return;
        }
        RegionManager manager = WGRegionUtils.getRegionManager(world);
        if (manager == null) {
            info(sender, "Регионы отключены в этом мире");
            return;
        }
        // One silent actor for all regions instead of a WorldGuard message per region.
        Actor actor = WGRegionUtils.wrapAsPrivileged(sender, true);
        int changed = 0;
        try {
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (!(region instanceof GlobalProtectedRegion)) {
                    AutoFlags.setFlag(actor, world, region, flag, value);
                    changed++;
                }
            }
            info(sender, "Флаги установлены (регионов: " + changed + ")");
        } catch (CommandException e) {
            info(sender, "Неправильный формат флага " + flag.getName() + ": " + e.getMessage());
        }
    }

    private void removeFromAllRegions(CommandSender sender, String name, boolean owners) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            removeFromAllRegions(sender, name, cached, owners);
            return;
        }
        // Unknown name may need a Mojang profile lookup, keep it off the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
            Bukkit.getScheduler().runTask(plugin, () -> removeFromAllRegions(sender, name, offlinePlayer, owners));
        });
    }

    private static void removeFromAllRegions(CommandSender sender, String input, OfflinePlayer offlinePlayer, boolean owners) {
        String name = (offlinePlayer.getName() != null ? offlinePlayer.getName() : input).toLowerCase(Locale.ROOT);
        UUID uuid = offlinePlayer.getUniqueId();
        int changed = 0;
        for (RegionManager manager : WGRegionUtils.getRegionContainer().getLoaded()) {
            for (ProtectedRegion region : manager.getRegions().values()) {
                DefaultDomain domain = owners ? region.getOwners() : region.getMembers();
                if (!domain.contains(uuid) && !domain.contains(name)) {
                    continue;
                }
                domain.removePlayer(uuid);
                domain.removePlayer(name);
                // setters mark the region dirty so it gets saved
                if (owners) {
                    region.setOwners(domain);
                } else {
                    region.setMembers(domain);
                }
                changed++;
            }
        }
        info(sender, "Игрок удалён из списков " + (owners ? "владельцев" : "участников") + " всех регионов (изменено регионов: " + changed + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matches(args[0], (sender instanceof Player ? PLAYER_SUBCOMMANDS : CONSOLE_SUBCOMMANDS).stream());
        }
        if (!args[0].equalsIgnoreCase("setflag")) {
            return List.of();
        }
        return switch (args.length) {
            case 2 -> matches(args[1], Bukkit.getWorlds().stream().map(World::getName));
            case 3 -> matches(args[2], WorldGuard.getInstance().getFlagRegistry().getAll().stream().map(Flag::getName));
            case 4 -> {
                Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), args[2]);
                if (flag instanceof StateFlag) {
                    yield matches(args[3], Stream.of(StateFlag.State.values()).map(Enum::name));
                } else if (flag instanceof BooleanFlag) {
                    yield matches(args[3], Stream.of("true", "false"));
                } else if (flag instanceof EnumFlag<?> enumFlag) {
                    yield matches(args[3], Stream.of(enumFlag.getEnumClass().getEnumConstants()).map(Enum::name));
                }
                yield List.of();
            }
            default -> List.of();
        };
    }

    private static List<String> matches(String token, Stream<String> options) {
        return StringUtil.copyPartialMatches(token, options.toList(), new ArrayList<>());
    }
}
