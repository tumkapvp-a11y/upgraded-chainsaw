package me.x1no4ekk.core.module.inventory;

import me.x1no4ekk.core.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Configured menu button: slot, icon material and MiniMessage name/lore templates.
 */
public record MenuButton(int slot, Material material, String name, List<String> lore) {

    public MenuButton {
        lore = List.copyOf(lore);
    }

    public ItemStack build(TagResolver... resolvers) {
        return ItemBuilder.of(material)
                .name(name, resolvers)
                .lore(lore, resolvers)
                .build();
    }
}
