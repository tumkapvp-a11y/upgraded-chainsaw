package me.x1no4ekk.core.module.teleport.tpa;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.module.teleport.TeleportConfig;
import me.x1no4ekk.core.util.CooldownTracker;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pending /tpa requests: target UUID -> (sender UUID -> expiry epoch millis), oldest first.
 * Only UUIDs are stored. A sweep timer runs while requests exist and notifies both sides on expiry.
 * Main thread only.
 */
public final class TpaManager {

    private final CorePlugin plugin;
    private final Supplier<TeleportConfig> config;
    private final Map<UUID, LinkedHashMap<UUID, Long>> requests = new HashMap<>();
    private final CooldownTracker cooldowns = new CooldownTracker();
    private BukkitTask sweepTask;

    public TpaManager(CorePlugin plugin, Supplier<TeleportConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean hasRequest(UUID target, UUID sender) {
        Map<UUID, Long> incoming = requests.get(target);
        if (incoming == null) {
            return false;
        }
        Long expiry = incoming.get(sender);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /**
     * @return milliseconds until the sender may send another request
     */
    public long cooldownRemaining(UUID sender) {
        return cooldowns.remaining(sender);
    }

    /**
     * Stores a request (replacing an older one from the same sender) and starts the sender's cooldown.
     */
    public void add(UUID target, UUID sender) {
        TeleportConfig.Tpa settings = config.get().tpa();
        LinkedHashMap<UUID, Long> incoming = requests.computeIfAbsent(target, key -> new LinkedHashMap<>());
        incoming.remove(sender);
        incoming.put(sender, System.currentTimeMillis() + settings.expireMillis());
        cooldowns.start(sender, settings.cooldownMillis());
        if (sweepTask == null) {
            sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
        }
    }

    /**
     * @return {@code true} if a request existed and was removed
     */
    public boolean remove(UUID target, UUID sender) {
        Map<UUID, Long> incoming = requests.get(target);
        if (incoming == null || incoming.remove(sender) == null) {
            return false;
        }
        if (incoming.isEmpty()) {
            requests.remove(target);
        }
        stopIfIdle();
        return true;
    }

    /**
     * @return the sender of the newest active request to {@code target}, or {@code null}
     */
    public UUID latestSender(UUID target) {
        List<UUID> senders = incomingSenders(target);
        return senders.isEmpty() ? null : senders.getLast();
    }

    /**
     * Senders of active requests to {@code target}, oldest first.
     */
    public List<UUID> incomingSenders(UUID target) {
        Map<UUID, Long> incoming = requests.get(target);
        if (incoming == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<UUID> result = new ArrayList<>();
        incoming.forEach((sender, expiry) -> {
            if (expiry > now) {
                result.add(sender);
            }
        });
        return result;
    }

    /**
     * Targets of active requests sent by {@code sender}.
     */
    public List<UUID> outgoingTargets(UUID sender) {
        long now = System.currentTimeMillis();
        List<UUID> result = new ArrayList<>();
        requests.forEach((target, incoming) -> {
            Long expiry = incoming.get(sender);
            if (expiry != null && expiry > now) {
                result.add(target);
            }
        });
        return result;
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        List<UUID[]> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Long>>> targets = requests.entrySet().iterator();
        while (targets.hasNext()) {
            Map.Entry<UUID, LinkedHashMap<UUID, Long>> entry = targets.next();
            Iterator<Map.Entry<UUID, Long>> senders = entry.getValue().entrySet().iterator();
            while (senders.hasNext()) {
                Map.Entry<UUID, Long> request = senders.next();
                if (request.getValue() <= now) {
                    senders.remove();
                    expired.add(new UUID[]{entry.getKey(), request.getKey()});
                }
            }
            if (entry.getValue().isEmpty()) {
                targets.remove();
            }
        }
        stopIfIdle();

        for (UUID[] pair : expired) {
            Player target = Bukkit.getPlayer(pair[0]);
            Player sender = Bukkit.getPlayer(pair[1]);
            if (target != null && sender != null) {
                plugin.messages().send(sender, "tpa.expired-sender", Placeholder.unparsed("player", target.getName()));
                plugin.messages().send(target, "tpa.expired-target", Placeholder.unparsed("player", sender.getName()));
            }
        }
    }

    private void stopIfIdle() {
        if (requests.isEmpty() && sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    /**
     * Quit hook: drops every request where the player is the target or the sender and tells the other side.
     */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        cooldowns.clear(uuid);

        // A player may be on both sides with the quitter; notify them once.
        Set<UUID> notify = new LinkedHashSet<>();
        Map<UUID, Long> incoming = requests.remove(uuid);
        if (incoming != null) {
            notify.addAll(incoming.keySet());
        }
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Long>>> targets = requests.entrySet().iterator();
        while (targets.hasNext()) {
            Map.Entry<UUID, LinkedHashMap<UUID, Long>> entry = targets.next();
            if (entry.getValue().remove(uuid) != null) {
                notify.add(entry.getKey());
            }
            if (entry.getValue().isEmpty()) {
                targets.remove();
            }
        }
        stopIfIdle();

        for (UUID other : notify) {
            Player online = Bukkit.getPlayer(other);
            if (online != null) {
                plugin.messages().send(online, "tpa.quit", Placeholder.unparsed("player", player.getName()));
            }
        }
    }

    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        requests.clear();
        cooldowns.clearAll();
    }
}
