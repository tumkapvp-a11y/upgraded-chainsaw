package wgextender.features.extendedwand;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import wgextender.utils.WEUtils;

public final class WEWand {

    private static final Component WAND_NAME = Component.text("Selection wand", NamedTextColor.LIGHT_PURPLE);
    /** Name of wands issued by WGExtender 3.x, they have no persistent data tag. */
    private static final String LEGACY_WAND_NAME = "§dSelection wand";

    private static NamespacedKey wandKey;
    private static String cachedWandItem;
    private static Material cachedWandMaterial;

    private WEWand() {
    }

    public static void init(Plugin plugin) {
        wandKey = new NamespacedKey(plugin, "selection_wand");
    }

    private static Material getWandMaterial() {
        String wandItem = WEUtils.getWorldEditPlugin().getLocalConfiguration().wandItem;
        if (!wandItem.equals(cachedWandItem)) {
            cachedWandMaterial = Material.matchMaterial(wandItem);
            cachedWandItem = wandItem;
        }
        return cachedWandMaterial;
    }

    public static ItemStack getWand() {
        Material material = getWandMaterial();
        ItemStack wand = ItemStack.of(material != null ? material : Material.WOODEN_AXE);
        wand.editMeta(meta -> {
            meta.displayName(WAND_NAME);
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BOOLEAN, true);
        });
        return wand;
    }

    public static boolean isWand(ItemStack item) {
        // Cheap checks first: this runs on every melee hit of a player.
        if (item == null || item.getType() != getWandMaterial() || !item.hasItemMeta()) {
            return false;
        }
        if (item.getPersistentDataContainer().has(wandKey)) {
            return true;
        }
        Component name = item.getItemMeta().displayName();
        return name != null && LEGACY_WAND_NAME.equals(LegacyComponentSerializer.legacySection().serialize(name));
    }
}
