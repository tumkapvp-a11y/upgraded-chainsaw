package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.user.UserManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Kill/death/mob-kill counters in the cached {@link User}; persisted by the user autosave.
 */
final class StatsListener implements Listener {

    private final UserManager users;

    StatsListener(UserManager users) {
        this.users = users;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        User victimUser = users.getUser(victim);
        if (victimUser != null) {
            victimUser.addDeath();
        }
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            User killerUser = users.getUser(killer);
            if (killerUser != null) {
                killerUser.addKill();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        // PlayerDeathEvent shares EntityDeathEvent's handler list; players are handled above.
        if (entity instanceof Player) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        User user = users.getUser(killer);
        if (user != null) {
            user.addMobKill();
        }
    }
}
