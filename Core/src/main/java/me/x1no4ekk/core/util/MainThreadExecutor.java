package me.x1no4ekk.core.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;

/**
 * Executor that hops back to the server thread for CompletableFuture continuations.
 * Tasks submitted after the plugin is disabled are dropped instead of throwing.
 */
public final class MainThreadExecutor implements Executor {

    private final Plugin plugin;

    public MainThreadExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
