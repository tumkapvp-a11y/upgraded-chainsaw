package me.x1no4ekk.core.module.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.vanish.VanishManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Iterator;
import java.util.UUID;

/**
 * Public chat: global/local routing, toggles, ignores, mute, cooldowns and rendering.
 * {@link #onChat} runs on an async thread and only reads thread-safe state.
 */
public final class ChatListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final CorePlugin plugin;
    private final ChatModule module;

    ChatListener(CorePlugin plugin, ChatModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ChatConfig config = module.config();
        User user = plugin.userManager().getUser(uuid);
        PlayerStates.Snapshot snapshot = module.states().get(uuid);
        if (user == null || snapshot == null) {
            event.setCancelled(true);
            module.notify(uuid, "general.data-not-loaded");
            return;
        }
        if (module.isMuted() && !player.hasPermission(ChatPermissions.BYPASS_MUTE)) {
            event.setCancelled(true);
            module.notify(uuid, "chat.muted");
            return;
        }
        boolean bypassCooldown = player.hasPermission(ChatPermissions.BYPASS_COOLDOWN);
        if (!bypassCooldown) {
            long joinWait = module.joinDelayRemaining(uuid, config.joinDelayMillis());
            if (joinWait > 0) {
                event.setCancelled(true);
                module.notify(uuid, "chat.join-delay", Placeholder.unparsed("time", Durations.formatMillis(joinWait)));
                return;
            }
            long left = module.chatCooldown().remaining(uuid);
            if (left > 0) {
                event.setCancelled(true);
                module.notify(uuid, "chat.cooldown", Placeholder.unparsed("time", Durations.formatMillis(left)));
                return;
            }
        }

        boolean global = !config.localEnabled();
        if (config.localEnabled()) {
            String raw = PLAIN.serialize(event.message());
            if (raw.startsWith(config.globalSymbol())) {
                String stripped = raw.substring(config.globalSymbol().length()).strip();
                if (stripped.isEmpty()) {
                    event.setCancelled(true);
                    return;
                }
                global = true;
                event.message(Component.text(stripped));
            }
        }
        if (!bypassCooldown) {
            module.chatCooldown().start(uuid, config.cooldownMillis());
        }

        boolean heard = filterViewers(event, uuid, snapshot, global, config,
                player.hasPermission(VanishManager.SEE_PERMISSION));
        if (!global && !heard && config.notifyNobodyHeard()) {
            module.notify(uuid, "chat.nobody-heard", Placeholder.unparsed("symbol", config.globalSymbol()));
        }

        ChatFormatter.Sender sender = module.formatter().player(player, snapshot, config);
        String format = global ? config.globalFormat() : config.localFormat();
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                module.formatter().render(format, sender, message)));
    }

    /**
     * Removes viewers that disabled chat, ignore the author or (local chat) are out of range.
     *
     * @return whether at least one other player the author can see still receives the message
     */
    private boolean filterViewers(AsyncChatEvent event, UUID author, PlayerStates.Snapshot origin, boolean global,
                                  ChatConfig config, boolean authorSeesVanished) {
        double radiusSquared = config.localRadius() * config.localRadius();
        boolean heard = false;
        Iterator<Audience> iterator = event.viewers().iterator();
        while (iterator.hasNext()) {
            if (!(iterator.next() instanceof Player viewer)) {
                continue;
            }
            UUID viewerId = viewer.getUniqueId();
            if (viewerId.equals(author)) {
                continue;
            }
            User viewerUser = plugin.userManager().getUser(viewerId);
            if (viewerUser != null && (!viewerUser.isChatToggle() || viewerUser.isIgnoring(author))) {
                iterator.remove();
                continue;
            }
            if (!global) {
                PlayerStates.Snapshot position = module.states().get(viewerId);
                if (position == null || !position.isNear(origin, radiusSquared)) {
                    iterator.remove();
                    continue;
                }
            }
            if (authorSeesVanished || !plugin.vanishManager().isVanished(viewerId)) {
                heard = true;
            }
        }
        return heard;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        module.recordJoin(player.getUniqueId());
        module.states().refresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        module.states().move(event.getPlayer().getUniqueId(), event.getTo());
    }
}
