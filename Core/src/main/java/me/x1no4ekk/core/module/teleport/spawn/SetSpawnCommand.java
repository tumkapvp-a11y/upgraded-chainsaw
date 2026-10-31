package me.x1no4ekk.core.module.teleport.spawn;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.command.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /setspawn — sets the server spawn to the sender's position.
 */
public final class SetSpawnCommand extends BaseCommand {

    private final SpawnManager spawns;

    public SetSpawnCommand(CorePlugin plugin, SpawnManager spawns) {
        super(plugin, "setspawn", "core.setspawn", "Установить точку спавна");
        this.spawns = spawns;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length > 0) {
            throw fail("spawn.setspawn-usage");
        }
        spawns.setSpawn(player);
    }
}
