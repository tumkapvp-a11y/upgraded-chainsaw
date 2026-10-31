package me.x1no4ekk.core.module.inventory;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.config.ConfigFile;
import me.x1no4ekk.core.module.CoreModule;
import me.x1no4ekk.core.module.inventory.enderchest.EnderChestCommand;
import me.x1no4ekk.core.module.inventory.enderchest.EnderChestListener;
import me.x1no4ekk.core.module.inventory.enderchest.EnderChestService;
import me.x1no4ekk.core.module.inventory.invsee.InvseeCommand;
import me.x1no4ekk.core.module.inventory.invsee.InvseeListener;
import me.x1no4ekk.core.module.inventory.kit.KitCommand;
import me.x1no4ekk.core.module.inventory.kit.KitListener;
import me.x1no4ekk.core.module.inventory.kit.KitService;
import me.x1no4ekk.core.module.inventory.kit.KitStorage;
import me.x1no4ekk.core.module.inventory.kit.KitsConfig;
import me.x1no4ekk.core.module.inventory.workstation.Workstation;
import me.x1no4ekk.core.module.inventory.workstation.WorkstationCommand;

/**
 * Kits (kits.yml), multi-page ender chest, /invsee and portable workstations (inventories.yml).
 * Uses the core tables {@code core_kit_cooldowns} and {@code core_enderchests}.
 */
public final class InventoryModule implements CoreModule {

    private static final String INVENTORIES_FILE = "inventories.yml";

    private final CorePlugin plugin;
    private volatile InventoriesConfig config;
    private KitStorage kitStorage;
    private KitService kitService;
    private EnderChestService enderChests;

    public InventoryModule(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "inventory";
    }

    @Override
    public void enable() throws Exception {
        kitStorage = new KitStorage(plugin);
        KitsConfig kits = kitStorage.load();
        config = InventoriesConfig.parse(ConfigFile.load(plugin, INVENTORIES_FILE));

        kitService = new KitService(plugin, kits);
        enderChests = new EnderChestService(plugin, () -> config.enderChest());

        plugin.registerListener(new KitListener(kitService));
        plugin.registerListener(new EnderChestListener(enderChests));
        plugin.registerListener(new InvseeListener());

        plugin.commands().register(new KitCommand(plugin, kitService, kitStorage));
        plugin.commands().register(new EnderChestCommand(plugin, enderChests));
        plugin.commands().register(new InvseeCommand(plugin, () -> config.invsee()));
        for (Workstation workstation : Workstation.values()) {
            plugin.commands().register(new WorkstationCommand(plugin, workstation, () -> config.workstationTitle(workstation)));
        }

        plugin.sessions().onQuit(player -> {
            kitService.onQuit(player);
            enderChests.onQuit(player);
            InvseeListener.closeViewersOf(player);
        });
        enderChests.start();
    }

    @Override
    public void reload() throws Exception {
        // Parse both files before applying either, so a broken file keeps the whole previous state.
        KitsConfig kits = kitStorage.load();
        InventoriesConfig inventories = InventoriesConfig.parse(ConfigFile.load(plugin, INVENTORIES_FILE));
        kitService.apply(kits);
        config = inventories;
    }

    @Override
    public void disable() {
        if (kitService != null) {
            kitService.shutdown();
        }
        if (enderChests != null) {
            enderChests.shutdown();
        }
        InvseeListener.closeAll();
    }
}
