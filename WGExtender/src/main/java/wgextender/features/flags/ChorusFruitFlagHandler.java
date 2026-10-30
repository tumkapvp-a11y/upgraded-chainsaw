package wgextender.features.flags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import wgextender.utils.WGRegionUtils;

public final class ChorusFruitFlagHandler implements Listener {

    private static final Component DENY_MESSAGE = Component.text("Вы не можете использовать хорус в этом регионе", NamedTextColor.RED);

    @EventHandler(ignoreCancelled = true)
    public void onItemUse(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.CHORUS_FRUIT) {
            return;
        }
        Player player = event.getPlayer();
        if (!WGRegionUtils.isFlagAllows(player, player.getLocation(), WGExtenderFlags.CHORUS_FRUIT_USE_FLAG)
            && !WGRegionUtils.canBypassProtection(player)) {
            player.sendMessage(DENY_MESSAGE);
            event.setCancelled(true);
        }
    }
}
