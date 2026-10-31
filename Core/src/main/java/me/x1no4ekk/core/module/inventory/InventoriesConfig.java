package me.x1no4ekk.core.module.inventory;

import me.x1no4ekk.core.config.Settings;
import me.x1no4ekk.core.module.inventory.enderchest.EnderChestSettings;
import me.x1no4ekk.core.module.inventory.workstation.Workstation;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable snapshot of inventories.yml: ender chest, /invsee and workstation titles.
 */
public record InventoriesConfig(EnderChestSettings enderChest, InvseeSettings invsee, Map<Workstation, String> workstationTitles) {

    public InventoriesConfig {
        workstationTitles = Map.copyOf(workstationTitles);
    }

    public record InvseeSettings(boolean logUsage, Settings.SoundSettings openSound) {
    }

    public static InventoriesConfig parse(ConfigurationSection root) {
        EnderChestSettings enderChest = EnderChestSettings.parse(ConfigSupport.section(root, "enderchest"));

        ConfigurationSection invsee = ConfigSupport.section(root, "invsee");
        InvseeSettings invseeSettings = new InvseeSettings(
                ConfigSupport.bool(invsee, "log-usage", true),
                ConfigSupport.sound(invsee, "open-sound")
        );

        ConfigurationSection workstations = ConfigSupport.section(root, "workstations");
        Map<Workstation, String> titles = new EnumMap<>(Workstation.class);
        for (Workstation workstation : Workstation.values()) {
            titles.put(workstation, ConfigSupport.string(workstations, workstation.configKey(), workstation.displayName()));
        }
        return new InventoriesConfig(enderChest, invseeSettings, titles);
    }

    public String workstationTitle(Workstation workstation) {
        return workstationTitles.get(workstation);
    }
}
