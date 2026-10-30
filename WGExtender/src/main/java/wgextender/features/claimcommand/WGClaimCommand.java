package wgextender.features.claimcommand;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion.CircularInheritanceException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import wgextender.utils.WEUtils;
import wgextender.utils.WGRegionUtils;

/**
 * /region claim with the same checks as WorldGuard 7.0.16, but limited to cuboid selections.
 */
public final class WGClaimCommand {

    private WGClaimCommand() {
    }

    static ProtectedRegion claim(String id, Player player) throws CommandException {
        LocalPlayer localPlayer = WGRegionUtils.wrapPlayer(player);
        RegionPermissionModel permModel = new RegionPermissionModel(localPlayer);
        if (!permModel.mayClaim()) {
            throw new CommandPermissionsException();
        }

        if (!ProtectedRegion.isValidId(id)) {
            throw new CommandException("Имя региона '" + id + "' содержит запрещённые символы.");
        }
        if (id.equalsIgnoreCase(ProtectedRegion.GLOBAL_REGION)) {
            throw new CommandException("Нельзя создать регион " + ProtectedRegion.GLOBAL_REGION + ".");
        }

        RegionManager manager = WGRegionUtils.getRegionManager(player.getWorld());
        if (manager == null) {
            throw new CommandException("Регионы отключены в этом мире.");
        }
        if (manager.hasRegion(id)) {
            throw new CommandException("Регион с таким именем уже существует. Пожалуйста, выберите другое имя.");
        }

        ProtectedRegion region = createProtectedRegionFromSelection(player, id);
        BukkitWorldConfiguration wcfg = WGRegionUtils.getWorldConfig(player.getWorld());

        if (!permModel.mayClaimRegionsUnbounded()) {
            int maxRegionCount = wcfg.getMaxRegionCount(localPlayer);
            if (maxRegionCount >= 0 && manager.getRegionCountOfPlayer(localPlayer) >= maxRegionCount) {
                throw new CommandException("У вас слишком много регионов, удалите хотя бы один перед созданием нового.");
            }
        }

        ApplicableRegionSet regions = manager.getApplicableRegions(region);
        if (regions.size() > 0) {
            if (!regions.isOwnerOfAll(localPlayer)) {
                throw new CommandException("Этот регион пересекается с чужим регионом.");
            }
        } else if (wcfg.claimOnlyInsideExistingRegions) {
            throw new CommandException("Вы можете создавать регионы только внутри своих регионов.");
        }

        if (wcfg.maxClaimVolume >= Integer.MAX_VALUE) {
            throw new CommandException("Максимально разрешенный конфигурацией размер региона больше допустимого. Он должен быть "
                + Integer.MAX_VALUE + " или меньше. Пожалуйста, сообщите об этом администратору сервера.");
        }
        if (!permModel.mayClaimRegionsUnbounded() && region.volume() > wcfg.maxClaimVolume) {
            throw new CommandException("Размер региона слишком большой. Максимальный размер: " + wcfg.maxClaimVolume
                + ", ваш размер: " + region.volume());
        }

        // set-parent-on-claim from WorldGuard config
        if (wcfg.setParentOnClaim != null && !wcfg.setParentOnClaim.isEmpty()) {
            ProtectedRegion template = manager.getRegion(wcfg.setParentOnClaim);
            if (template != null) {
                try {
                    region.setParent(template);
                } catch (CircularInheritanceException e) {
                    throw new CommandException(e.getMessage());
                }
            }
        }

        region.getOwners().addPlayer(localPlayer);
        manager.addRegion(region);
        player.sendMessage(Component.text("Вы создали регион " + id, NamedTextColor.YELLOW));
        return region;
    }

    private static ProtectedRegion createProtectedRegionFromSelection(Player player, String id) throws CommandException {
        try {
            Region selection = WEUtils.getSelection(player);
            if (!(selection instanceof CuboidRegion)) {
                throw new CommandException("Вы можете использовать только кубическую территорию.");
            }
            return new ProtectedCuboidRegion(id, selection.getMinimumPoint(), selection.getMaximumPoint());
        } catch (IncompleteRegionException e) {
            throw new CommandException("Для начала выделите территорию!");
        }
    }
}
