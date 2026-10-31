package me.x1no4ekk.core.module.inventory.workstation;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.builder.LocationInventoryViewBuilder;

/**
 * Portable workstation menus opened through {@link MenuType} builders.
 */
public enum Workstation {

    WORKBENCH("workbench", "core.workbench", "Верстак", "workbench", MenuType.CRAFTING, "wb", "craft"),
    ANVIL("anvil", "core.anvil", "Наковальня", "anvil", MenuType.ANVIL),
    CARTOGRAPHY_TABLE("carttable", "core.carttable", "Стол картографа", "cartography-table", MenuType.CARTOGRAPHY_TABLE, "cartographytable"),
    ENCHANTING_TABLE("etable", "core.etable", "Стол зачарований", "enchanting-table", MenuType.ENCHANTMENT, "enchanttable"),
    GRINDSTONE("grindstone", "core.grindstone", "Точило", "grindstone", MenuType.GRINDSTONE),
    LOOM("loom", "core.loom", "Ткацкий станок", "loom", MenuType.LOOM),
    SMITHING_TABLE("smithtable", "core.smithtable", "Кузнечный стол", "smithing-table", MenuType.SMITHING, "smithingtable"),
    STONECUTTER("stonecutter", "core.stonecutter", "Камнерез", "stonecutter", MenuType.STONECUTTER);

    private final String label;
    private final String permission;
    private final String displayName;
    private final String configKey;
    private final MenuType.Typed<?, ? extends LocationInventoryViewBuilder<?>> menu;
    private final String[] aliases;

    Workstation(String label, String permission, String displayName, String configKey,
                MenuType.Typed<?, ? extends LocationInventoryViewBuilder<?>> menu, String... aliases) {
        this.label = label;
        this.permission = permission;
        this.displayName = displayName;
        this.configKey = configKey;
        this.menu = menu;
        this.aliases = aliases;
    }

    public String label() {
        return label;
    }

    public String permission() {
        return permission;
    }

    /**
     * Russian name, used as the command description and as the fallback menu title.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Key under {@code workstations} in inventories.yml.
     */
    public String configKey() {
        return configKey;
    }

    public String[] aliases() {
        return aliases.clone();
    }

    /**
     * Opens the menu bound to the player's position: the location gives the menu a level access, without which
     * crafting results and enchantment offers are never computed and input items would not be returned on close.
     * Reachability is not checked, so the menu stays open wherever the player walks.
     */
    public void open(Player player, Component title) {
        menu.builder()
                .title(title)
                .checkReachable(false)
                .location(player.getLocation())
                .build(player)
                .open();
    }
}
