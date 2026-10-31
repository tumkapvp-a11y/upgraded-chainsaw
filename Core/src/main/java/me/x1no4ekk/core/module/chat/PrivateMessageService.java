package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.CommandFailure;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.CooldownTracker;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.util.Sounds;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Private messages, reply partners, social spy delivery and target spying. Main thread only.
 */
public final class PrivateMessageService implements Listener {

    private final CorePlugin plugin;
    private final ChatModule module;
    private final CooldownTracker cooldown = new CooldownTracker();
    /** Player -> last conversation partner (set in both directions). */
    private final Map<UUID, UUID> partners = new HashMap<>();
    /** Spy -> watched player. */
    private final Map<UUID, UUID> targetSpies = new HashMap<>();

    PrivateMessageService(CorePlugin plugin, ChatModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /**
     * Delivers a private message after all checks.
     *
     * @throws CommandFailure with the reason the message was not sent
     */
    void send(Player sender, Player target, String text) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            throw new CommandFailure("msg.self");
        }
        User senderUser = plugin.userManager().getUser(sender);
        if (senderUser == null) {
            throw new CommandFailure("general.data-not-loaded");
        }
        User targetUser = plugin.userManager().getUser(target);
        TagResolver targetName = Placeholder.unparsed("player", target.getName());
        // Ignoring is deliberately reported as a generic refusal.
        if (targetUser == null || targetUser.isIgnoring(sender.getUniqueId())) {
            throw new CommandFailure("msg.not-accepting", targetName);
        }
        if (!targetUser.isMsgToggle() && !sender.hasPermission(ChatPermissions.MSG_BYPASS_TOGGLE)) {
            throw new CommandFailure("msg.target-disabled", targetName);
        }
        ChatConfig.PrivateSettings settings = module.config().privateMessages();
        if (!sender.hasPermission(ChatPermissions.MSG_BYPASS_COOLDOWN)) {
            long left = cooldown.remaining(sender.getUniqueId());
            if (left > 0) {
                throw new CommandFailure("msg.cooldown", Placeholder.unparsed("time", Durations.formatMillis(left)));
            }
            cooldown.start(sender.getUniqueId(), settings.cooldownMillis());
        }

        TagResolver[] resolvers = {
                Placeholder.unparsed("sender", sender.getName()),
                Placeholder.unparsed("receiver", target.getName()),
                Placeholder.unparsed("message", text)
        };
        sender.sendMessage(Text.parse(settings.formatSender(), resolvers));
        target.sendMessage(Text.parse(settings.formatReceiver(), resolvers));
        Sounds.play(target, settings.sound());

        partners.put(sender.getUniqueId(), target.getUniqueId());
        partners.put(target.getUniqueId(), sender.getUniqueId());

        Component spyLine = Text.parse(settings.formatSpy(), resolvers);
        for (Player spy : Bukkit.getOnlinePlayers()) {
            UUID spyId = spy.getUniqueId();
            if (spyId.equals(sender.getUniqueId()) || spyId.equals(target.getUniqueId())) {
                continue;
            }
            if (isSocialSpy(spy) || watches(spy, sender.getUniqueId()) || watches(spy, target.getUniqueId())) {
                spy.sendMessage(spyLine);
            }
        }
    }

    private boolean isSocialSpy(Player spy) {
        User user = plugin.userManager().getUser(spy);
        return user != null && user.isSocialSpy() && spy.hasPermission(ChatPermissions.SOCIAL_SPY);
    }

    private boolean watches(Player spy, UUID target) {
        return target.equals(targetSpies.get(spy.getUniqueId())) && spy.hasPermission(ChatPermissions.TARGET_SPY);
    }

    @Nullable UUID partner(UUID player) {
        return partners.get(player);
    }

    @Nullable UUID spyTarget(UUID spy) {
        return targetSpies.get(spy);
    }

    void startSpying(UUID spy, UUID target) {
        targetSpies.put(spy, target);
    }

    @Nullable UUID stopSpying(UUID spy) {
        return targetSpies.remove(spy);
    }

    /**
     * Copies every command of a watched player to their spies, including cancelled ones.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (targetSpies.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Component line = null;
        for (Map.Entry<UUID, UUID> entry : targetSpies.entrySet()) {
            if (!entry.getValue().equals(uuid)) {
                continue;
            }
            Player spy = Bukkit.getPlayer(entry.getKey());
            if (spy == null || !spy.hasPermission(ChatPermissions.TARGET_SPY)) {
                continue;
            }
            if (line == null) {
                line = Text.parse(module.config().privateMessages().formatCommandSpy(),
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.unparsed("command", event.getMessage()));
            }
            spy.sendMessage(line);
        }
    }

    void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        cooldown.clear(uuid);
        partners.remove(uuid);
        partners.values().removeIf(uuid::equals);
        targetSpies.remove(uuid);
        Iterator<Map.Entry<UUID, UUID>> iterator = targetSpies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (entry.getValue().equals(uuid)) {
                iterator.remove();
                Player spy = Bukkit.getPlayer(entry.getKey());
                if (spy != null) {
                    plugin.messages().send(spy, "spy.target-quit", Placeholder.unparsed("player", player.getName()));
                }
            }
        }
    }

    void clear() {
        cooldown.clearAll();
        partners.clear();
        targetSpies.clear();
    }
}
