package me.x1no4ekk.core.command;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.x1no4ekk.core.CorePlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Collects commands during onEnable and registers them in Paper's COMMANDS lifecycle event.
 * The main label overrides vanilla commands with the same name (/give, /tp, /gamemode...).
 */
public final class CommandRegistry {

    private final CorePlugin plugin;
    private final Map<String, BaseCommand> commands = new LinkedHashMap<>();
    private boolean installed;

    public CommandRegistry(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public void register(BaseCommand command) {
        if (installed) {
            throw new IllegalStateException("Commands must be registered during onEnable");
        }
        BaseCommand previous = commands.putIfAbsent(command.name(), command);
        if (previous != null) {
            plugin.getLogger().warning("Duplicate command /" + command.name() + " ignored");
        }
    }

    public Collection<BaseCommand> commands() {
        return Collections.unmodifiableCollection(commands.values());
    }

    /**
     * Hooks the registrar; called once after all modules registered their commands.
     * Commands listed in settings.yml "disabled-commands" are skipped.
     */
    public void install() {
        installed = true;
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            Set<String> disabled = plugin.settings().disabledCommands();
            for (BaseCommand command : commands.values()) {
                if (disabled.contains(command.name())) {
                    continue;
                }
                registrar.register(command.name(), command.description(), command.aliases(), command);
            }
        });
    }
}
