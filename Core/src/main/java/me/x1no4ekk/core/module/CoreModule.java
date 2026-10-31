package me.x1no4ekk.core.module;

/**
 * Self-contained feature of Core (teleport, chat, kits...).
 * <p>
 * Lifecycle: {@link #enable()} runs inside onEnable after MySQL, the user cache and shared services are ready.
 * A module loads its own YAML file, creates its extra tables (blocking DDL is allowed only here), registers
 * listeners via {@code plugin.registerListener}, commands via {@code plugin.commands().register} and
 * per-player cleanup via {@code plugin.sessions().onQuit}.
 */
public interface CoreModule {

    String name();

    void enable() throws Exception;

    /**
     * Called by /core reload. Throwing keeps the previous configuration.
     */
    default void reload() throws Exception {
    }

    /**
     * Called in onDisable in reverse order. Must cancel tasks, close menus and flush pending writes.
     */
    default void disable() {
    }
}
