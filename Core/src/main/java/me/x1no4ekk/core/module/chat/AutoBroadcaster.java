package me.x1no4ekk.core.module.chat;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.util.Sounds;
import me.x1no4ekk.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodic announcements from chat.yml auto-broadcast. One repeating task, restarted on reload.
 */
final class AutoBroadcaster {

    private final CorePlugin plugin;
    private final ChatModule module;
    private BukkitTask task;
    private int next;
    private int last = -1;

    AutoBroadcaster(CorePlugin plugin, ChatModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    void restart(ChatConfig.AutoBroadcastSettings settings) {
        stop();
        next = 0;
        last = -1;
        if (!settings.enabled() || settings.messages().isEmpty()) {
            return;
        }
        long period = settings.intervalSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::announce, period, period);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void announce() {
        ChatConfig.AutoBroadcastSettings settings = module.config().autoBroadcast();
        List<String> messages = settings.messages();
        if (messages.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        String template = messages.get(pick(settings.random(), messages.size()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Text.parse(template, plugin.placeholders().tag(player)));
            Sounds.play(player, settings.sound());
        }
    }

    private int pick(boolean random, int size) {
        int index;
        if (random) {
            // Avoid showing the same announcement twice in a row.
            index = size == 1 ? 0 : ThreadLocalRandom.current().nextInt(size - 1);
            if (size > 1 && index >= last && last >= 0) {
                index++;
            }
        } else {
            index = next % size;
            next = (index + 1) % size;
        }
        last = index;
        return index;
    }
}
