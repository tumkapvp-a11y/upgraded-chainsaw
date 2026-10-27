package dev.applesub.feature;

import dev.applesub.AppleSubPlugin;
import dev.applesub.data.Feature;
import dev.applesub.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public final class AntiTrashListener implements Listener {

    private final AppleSubPlugin plugin;

    public AntiTrashListener(AppleSubPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = plugin.dataManager().get(player.getUniqueId());
        if (data == null || !data.isActive(Feature.ANTI_TRASH)) {
            return;
        }
        Material material = event.getItem().getItemStack().getType();
        if (plugin.settings().isDefaultTrash(material) || data.customTrash().contains(material)) {
            event.setCancelled(true);
        }
    }
}
