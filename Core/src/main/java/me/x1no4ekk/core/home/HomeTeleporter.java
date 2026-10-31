package me.x1no4ekk.core.home;

import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.combat.CombatManager;
import me.x1no4ekk.core.config.Messages;
import me.x1no4ekk.core.horn.TeleportHorn;
import me.x1no4ekk.core.teleport.TeleportOptions;
import me.x1no4ekk.core.teleport.TeleportService;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.user.UserManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Teleport flows started from {@link me.x1no4ekk.core.gui.HomeGui}. Continuations re-resolve the player
 * by UUID because the player may log out while the destination chunk is loading.
 */
public final class HomeTeleporter {

    public static final String BYPASS_COOLDOWN_PERMISSION = "core.horn.bypass-cooldown";

    private final UserManager userManager;
    private final TeleportService teleportService;
    private final CombatManager combatManager;
    private final TeleportHorn horn;
    private final Messages messages;
    private final Executor mainThread;

    public HomeTeleporter(UserManager userManager, TeleportService teleportService, CombatManager combatManager,
                          TeleportHorn horn, Messages messages, Executor mainThread) {
        this.userManager = userManager;
        this.teleportService = teleportService;
        this.combatManager = combatManager;
        this.horn = horn;
        this.messages = messages;
        this.mainThread = mainThread;
    }

    /**
     * @return {@code true} if the horn is recharging (a message has been sent)
     */
    public boolean checkCooldown(Player player) {
        if (player.hasPermission(BYPASS_COOLDOWN_PERMISSION) || !player.hasCooldown(Material.GOAT_HORN)) {
            return false;
        }
        int seconds = (player.getCooldown(Material.GOAT_HORN) + 19) / 20;
        messages.send(player, "horn.cooldown", Placeholder.unparsed("seconds", String.valueOf(seconds)));
        return true;
    }

    public void teleportWithHorn(Player player, String homeName) {
        User user = userManager.getUser(player);
        if (user == null) {
            messages.send(player, "general.data-not-loaded");
            return;
        }
        Home home = user.getHome(homeName);
        if (home == null) {
            messages.send(player, "home.not-found", Placeholder.unparsed("home", homeName));
            return;
        }
        if (checkCooldown(player)) {
            return;
        }
        if (home.toLocation() == null) {
            messages.send(player, "home.world-not-loaded", Placeholder.unparsed("world", home.world()));
            return;
        }
        if (combatManager.isInCombat(player.getUniqueId()) && !player.hasPermission(CombatManager.BYPASS_PERMISSION)) {
            messages.send(player, "teleport.in-combat",
                    Placeholder.unparsed("seconds", String.valueOf(combatManager.getRemainingSeconds(player.getUniqueId()))));
            return;
        }

        horn.playSound(player);
        UUID uuid = player.getUniqueId();
        teleportService.teleport(player, home.toLocation(), TeleportOptions.HORN).thenAcceptAsync(result -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                return;
            }
            if (result == TeleportResult.SUCCESS) {
                messages.send(online, "home.teleported", Placeholder.unparsed("home", home.name()));
                int cooldown = horn.cooldownTicks();
                if (cooldown > 0 && !online.hasPermission(BYPASS_COOLDOWN_PERMISSION)) {
                    online.setCooldown(Material.GOAT_HORN, cooldown);
                }
            }
        }, mainThread);
    }

    public void teleportToOther(Player player, String ownerName, Home home) {
        if (home.toLocation() == null) {
            messages.send(player, "home.world-not-loaded", Placeholder.unparsed("world", home.world()));
            return;
        }
        UUID uuid = player.getUniqueId();
        teleportService.teleport(player, home.toLocation(), TeleportOptions.ADMIN).thenAcceptAsync(result -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && result == TeleportResult.SUCCESS) {
                messages.send(online, "home.player-teleported",
                        Placeholder.unparsed("home", home.name()),
                        Placeholder.unparsed("player", ownerName));
            }
        }, mainThread);
    }
}
