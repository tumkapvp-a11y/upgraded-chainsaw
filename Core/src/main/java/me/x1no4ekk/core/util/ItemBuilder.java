package me.x1no4ekk.core.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Small fluent builder over {@link ItemStack#editMeta}.
 */
public final class ItemBuilder {

    private final ItemStack item;

    private ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, amount));
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    public ItemBuilder name(Component name) {
        item.editMeta(meta -> meta.displayName(name));
        return this;
    }

    public ItemBuilder name(String miniMessage, TagResolver... resolvers) {
        return name(Text.item(miniMessage, resolvers));
    }

    public ItemBuilder lore(List<Component> lore) {
        item.editMeta(meta -> meta.lore(lore.isEmpty() ? null : lore));
        return this;
    }

    public ItemBuilder lore(List<String> miniMessageLines, TagResolver... resolvers) {
        return lore(Text.itemLines(miniMessageLines, resolvers));
    }

    public ItemBuilder hideTooltip() {
        item.editMeta(meta -> meta.setHideTooltip(true));
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        item.editMeta(meta -> meta.addItemFlags(flags));
        return this;
    }

    public ItemBuilder glint(boolean glint) {
        if (glint) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.clamp(amount, 1, item.getMaxStackSize()));
        return this;
    }

    /**
     * Sets the head owner (PLAYER_HEAD only). Online players' profiles already contain skin textures.
     */
    public ItemBuilder head(PlayerProfile profile) {
        item.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        return this;
    }

    public ItemStack build() {
        return item;
    }
}
