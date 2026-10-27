package dev.applesub.feature;

import dev.applesub.AppleSubPlugin;
import dev.applesub.data.Feature;
import dev.applesub.data.PlayerData;
import dev.applesub.data.PlayerDataManager;
import dev.applesub.service.SubscriptionService;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Once a second: ends expired subscriptions and restores night vision (after death, milk, etc.). */
public final class FeatureTask extends BukkitRunnable {

    private final AppleSubPlugin plugin;

    public FeatureTask(AppleSubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PlayerDataManager manager = plugin.dataManager();
        SubscriptionService service = plugin.service();
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerData data = manager.get(player.getUniqueId());
            if (data == null) {
                continue;
            }
            long expiresAt = data.expiresAt();
            if (expiresAt > 0 && expiresAt <= now) {
                service.expire(player, data);
                continue;
            }
            if (data.isActive(Feature.NIGHT_VISION) && !player.isDead()) {
                NightVision.ensure(player);
            }
        }
    }
}
