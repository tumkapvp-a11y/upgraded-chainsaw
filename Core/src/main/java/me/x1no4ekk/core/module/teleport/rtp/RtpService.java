package me.x1no4ekk.core.module.teleport.rtp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.api.TeleportResult;
import me.x1no4ekk.core.combat.CombatManager;
import me.x1no4ekk.core.module.teleport.TeleportConfig;
import me.x1no4ekk.core.teleport.TeleportOptions;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Durations;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Random teleport: validation, one search per player, teleport with player options, and cooldown/charge only
 * after a successful teleport. Main thread only.
 */
public final class RtpService {

    public static final String BYPASS_COOLDOWN_PERMISSION = "core.rtp.bypass-cooldown";

    private final CorePlugin plugin;
    private final Supplier<TeleportConfig> config;
    private final RtpLocationFinder finder;
    /**
     * Players with a search or RTP warmup in progress. The value is a per-search token, so a search that
     * outlived its player's session (quit and rejoin) is recognised and dropped.
     */
    private final Map<UUID, Object> active = new HashMap<>();

    public RtpService(CorePlugin plugin, Supplier<TeleportConfig> config) {
        this.plugin = plugin;
        this.config = config;
        this.finder = new RtpLocationFinder(plugin);
    }

    public boolean hasAccess(Player player, TeleportConfig.RtpOption option) {
        return option.permission() == null || player.hasPermission(option.permission());
    }

    /**
     * @return milliseconds left on the option's cooldown for this player, 0 if none or bypassed
     */
    public long cooldownRemaining(Player player, User user, TeleportConfig.RtpOption option) {
        if (option.cooldownMillis() <= 0 || player.hasPermission(BYPASS_COOLDOWN_PERMISSION)) {
            return 0;
        }
        return plugin.userManager().getCooldownRemaining(user, option.cooldownKey());
    }

    public static String formatPrice(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public void start(Player player, TeleportConfig.RtpOption option) {
        UUID uuid = player.getUniqueId();
        if (!hasAccess(player, option)) {
            plugin.messages().send(player, "rtp.no-permission");
            return;
        }
        if (active.containsKey(uuid)) {
            plugin.messages().send(player, "rtp.already-searching");
            return;
        }
        User user = plugin.userManager().getUser(uuid);
        if (user == null) {
            plugin.messages().send(player, "general.data-not-loaded");
            return;
        }
        long cooldown = cooldownRemaining(player, user, option);
        if (cooldown > 0) {
            plugin.messages().send(player, "rtp.cooldown", Placeholder.unparsed("time", Durations.formatMillis(cooldown)));
            return;
        }
        World world = Bukkit.getWorld(option.world());
        if (world == null) {
            plugin.messages().send(player, "rtp.world-not-found", Placeholder.unparsed("world", option.world()));
            return;
        }
        if (!canPay(player, option)) {
            return;
        }
        CombatManager combat = plugin.combatManager();
        if (combat.isInCombat(uuid) && !player.hasPermission(CombatManager.BYPASS_PERMISSION)) {
            plugin.messages().send(player, "teleport.in-combat",
                    Placeholder.unparsed("seconds", String.valueOf(combat.getRemainingSeconds(uuid))));
            return;
        }

        double centerX;
        double centerZ;
        if (option.type() == TeleportConfig.RtpType.NEAR_PLAYER) {
            Player anchor = randomAnchor(player, world);
            if (anchor == null) {
                plugin.messages().send(player, "rtp.no-players");
                return;
            }
            centerX = anchor.getLocation().getX();
            centerZ = anchor.getLocation().getZ();
        } else if (option.centerAtSpawn()) {
            Location spawn = world.getSpawnLocation();
            centerX = spawn.getX();
            centerZ = spawn.getZ();
        } else {
            centerX = option.centerX();
            centerZ = option.centerZ();
        }

        Object token = new Object();
        active.put(uuid, token);
        plugin.messages().send(player, "rtp.searching");
        finder.find(world, centerX, centerZ, option.minRadius(), option.maxRadius(), option.maxAttempts(),
                        config.get().rtp().unsafeBlocks())
                .whenCompleteAsync((location, error) -> onSearchDone(uuid, token, option, location, error),
                        plugin.mainThread());
    }

    /**
     * Checks the price before searching (and again before teleporting); sends the reason when it fails.
     */
    private boolean canPay(Player player, TeleportConfig.RtpOption option) {
        if (option.price() <= 0) {
            return true;
        }
        if (!plugin.vault().hasEconomy()) {
            plugin.messages().send(player, "rtp.economy-unavailable");
            return false;
        }
        if (!plugin.vault().has(player, option.price())) {
            plugin.messages().send(player, "rtp.not-enough-money",
                    Placeholder.unparsed("price", formatPrice(option.price())),
                    Placeholder.unparsed("balance", formatPrice(plugin.vault().balance(player))));
            return false;
        }
        return true;
    }

    /**
     * A random visible (not vanished, not spectating) other player in the world, or {@code null}.
     */
    private Player randomAnchor(Player player, World world) {
        List<Player> candidates = new ArrayList<>();
        for (Player other : world.getPlayers()) {
            if (!other.equals(player)
                    && other.getGameMode() != GameMode.SPECTATOR
                    && !plugin.vanishManager().isVanished(other.getUniqueId())) {
                candidates.add(other);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private void onSearchDone(UUID uuid, Object token, TeleportConfig.RtpOption option, Location location, Throwable error) {
        if (active.get(uuid) != token) {
            return; // the player left (or the module stopped) while searching
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            active.remove(uuid, token);
            return;
        }
        if (error != null) {
            plugin.getLogger().log(Level.SEVERE, "RTP search failed for " + player.getName(), error);
        }
        if (location == null) {
            active.remove(uuid, token);
            plugin.messages().send(player, "rtp.not-found");
            return;
        }
        if (!canPay(player, option)) {
            active.remove(uuid, token);
            return;
        }
        Location current = player.getLocation();
        location.setYaw(current.getYaw());
        location.setPitch(current.getPitch());
        plugin.teleportService().teleport(player, location, TeleportOptions.PLAYER)
                .whenCompleteAsync((result, failure) -> onTeleported(uuid, token, option, result), plugin.mainThread());
    }

    private void onTeleported(UUID uuid, Object token, TeleportConfig.RtpOption option, TeleportResult result) {
        active.remove(uuid, token);
        Player player = Bukkit.getPlayer(uuid);
        if (result != TeleportResult.SUCCESS || player == null) {
            return;
        }
        User user = plugin.userManager().getUser(uuid);
        if (user != null && option.cooldownMillis() > 0 && !player.hasPermission(BYPASS_COOLDOWN_PERMISSION)) {
            plugin.userManager().startCooldown(user, option.cooldownKey(), option.cooldownMillis());
        }

        Location arrival = player.getLocation();
        if (option.type() == TeleportConfig.RtpType.NEAR_PLAYER) {
            plugin.messages().send(player, "rtp.teleported-near",
                    Placeholder.unparsed("players", String.valueOf(countNearby(player, arrival, option.maxRadius()))));
        } else {
            plugin.messages().send(player, "rtp.teleported",
                    Placeholder.unparsed("world", arrival.getWorld().getName()),
                    Placeholder.unparsed("x", String.valueOf(arrival.getBlockX())),
                    Placeholder.unparsed("y", String.valueOf(arrival.getBlockY())),
                    Placeholder.unparsed("z", String.valueOf(arrival.getBlockZ())));
        }

        if (option.price() > 0) {
            String price = formatPrice(option.price());
            if (plugin.vault().withdraw(player, option.price())) {
                plugin.messages().send(player, "rtp.charged", Placeholder.unparsed("price", price));
            } else {
                plugin.getLogger().warning("RTP: could not withdraw " + price + " from " + player.getName()
                        + " after teleport (option " + option.id() + ")");
                plugin.messages().send(player, "rtp.payment-failed", Placeholder.unparsed("price", price));
            }
        }
    }

    private int countNearby(Player player, Location arrival, int radius) {
        double radiusSquared = (double) radius * radius;
        int count = 0;
        for (Player other : arrival.getWorld().getPlayers()) {
            if (!other.equals(player)
                    && !plugin.vanishManager().isVanished(other.getUniqueId())
                    && other.getLocation().distanceSquared(arrival) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    /**
     * Quit hook: forgets the player's search; its late result is ignored thanks to the token.
     */
    public void handleQuit(Player player) {
        active.remove(player.getUniqueId());
    }

    public void shutdown() {
        active.clear();
    }
}
