package me.x1no4ekk.core.module.inventory.kit;

import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable kit definition from kits.yml. {@link #items()} holds private copies: hand out {@link #copyItems()} only.
 *
 * @param cooldownSeconds 0 = no cooldown, -1 = one-time kit, otherwise seconds between claims
 * @param price           price in АР; 0 = free
 * @param iconName        MiniMessage icon name, or {@code null} to use the menu's default template
 */
public record Kit(
        String id,
        String displayName,
        int slot,
        Material iconMaterial,
        @Nullable String iconName,
        List<String> iconLore,
        boolean iconGlint,
        long cooldownSeconds,
        double price,
        boolean autoEquip,
        boolean firstJoin,
        List<ItemStack> items
) {

    public static final String PERMISSION_PREFIX = "core.kit.";

    public Kit {
        iconLore = List.copyOf(iconLore);
        items = items.stream().map(ItemStack::clone).toList();
    }

    public String permission() {
        return PERMISSION_PREFIX + id;
    }

    public boolean oneTime() {
        return cooldownSeconds < 0;
    }

    public List<ItemStack> copyItems() {
        List<ItemStack> copies = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            copies.add(item.clone());
        }
        return copies;
    }

    public Component displayComponent() {
        return Text.parse(displayName);
    }
}
