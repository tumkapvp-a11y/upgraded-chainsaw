package dev.applesub.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class Items {

    private Items() {
    }

    public static ItemStack create(Material material, Component name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            if (name != null) {
                meta.displayName(name);
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    public static ItemStack glow(ItemStack item, boolean glow) {
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null));
        return item;
    }

    public static ItemStack pane(Material material) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }

    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
