package me.x1no4ekk.core.module.inventory.kit;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kit item codec. An entry of {@code items} is either a readable map
 * ({@code material, amount, name, lore, enchantments, unbreakable}) or {@code "base64:<data>"},
 * the Base64 of {@link ItemStack#serializeAsBytes()} (written by /kit create, keeps every component).
 */
public final class KitItems {

    public static final String BASE64_PREFIX = "base64:";
    /** A whole player inventory of full stacks. */
    private static final int MAX_AMOUNT = 64 * 41;

    private KitItems() {
    }

    public static String encode(ItemStack item) {
        return BASE64_PREFIX + Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /**
     * @return one or more stacks (a readable amount above the stack size is split)
     */
    public static List<ItemStack> parse(Object entry, String path) {
        if (entry instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.regionMatches(true, 0, BASE64_PREFIX, 0, BASE64_PREFIX.length())) {
                throw new IllegalArgumentException(path + ": expected '" + BASE64_PREFIX + "...' or an item section");
            }
            return List.of(decode(trimmed.substring(BASE64_PREFIX.length()).trim(), path));
        }
        if (entry instanceof Map<?, ?> map) {
            return readable(map, path);
        }
        if (entry instanceof ConfigurationSection section) {
            return readable(section.getValues(false), path);
        }
        throw new IllegalArgumentException(path + ": unsupported item entry '" + entry + "'");
    }

    private static ItemStack decode(String data, String path) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ": invalid Base64 data", e);
        }
        ItemStack item;
        try {
            item = ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(path + ": cannot decode item: " + e.getMessage(), e);
        }
        if (item.isEmpty()) {
            throw new IllegalArgumentException(path + ": decoded item is empty");
        }
        return item;
    }

    private static List<ItemStack> readable(Map<?, ?> map, String path) {
        Object materialRaw = map.get("material");
        Material material = materialRaw == null ? null : Material.matchMaterial(materialRaw.toString().trim());
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException(path + ".material: unknown item material '" + materialRaw + "'");
        }
        int amount = intValue(map.get("amount"), 1, path + ".amount");
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException(path + ".amount must be between 1 and " + MAX_AMOUNT);
        }
        Component name = map.get("name") == null ? null : Text.item(map.get("name").toString());
        List<Component> lore = lore(map.get("lore"));
        Map<Enchantment, Integer> enchantments = enchantments(map.get("enchantments"), path + ".enchantments");
        boolean unbreakable = booleanValue(map.get("unbreakable"), path + ".unbreakable");

        ItemStack template = ItemStack.of(material);
        template.editMeta(meta -> {
            if (name != null) {
                meta.displayName(name);
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            if (unbreakable) {
                meta.setUnbreakable(true);
            }
            enchantments.forEach((enchantment, level) -> {
                if (meta instanceof EnchantmentStorageMeta storage) {
                    storage.addStoredEnchant(enchantment, level, true);
                } else {
                    meta.addEnchant(enchantment, level, true);
                }
            });
        });

        int stackSize = Math.max(1, template.getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        for (int left = amount; left > 0; left -= stackSize) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(left, stackSize));
            stacks.add(stack);
        }
        return stacks;
    }

    private static List<Component> lore(Object raw) {
        List<Component> lines = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object line : list) {
                lines.add(Text.item(String.valueOf(line)));
            }
        } else if (raw != null) {
            lines.add(Text.item(raw.toString()));
        }
        return lines;
    }

    private static Map<Enchantment, Integer> enchantments(Object raw, String path) {
        if (raw == null) {
            return Map.of();
        }
        Map<?, ?> entries;
        if (raw instanceof Map<?, ?> map) {
            entries = map;
        } else if (raw instanceof ConfigurationSection section) {
            entries = section.getValues(false);
        } else {
            throw new IllegalArgumentException(path + ": expected a map like {sharpness: 5}");
        }
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String keyRaw = String.valueOf(entry.getKey()).trim().toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(keyRaw);
            Enchantment enchantment = key == null ? null : registry.get(key);
            if (enchantment == null) {
                throw new IllegalArgumentException(path + ": unknown enchantment '" + entry.getKey() + "'");
            }
            int level = intValue(entry.getValue(), 1, path + "." + keyRaw);
            if (level < 1 || level > 255) {
                throw new IllegalArgumentException(path + "." + keyRaw + " level must be between 1 and 255");
            }
            result.put(enchantment, level);
        }
        return result;
    }

    private static int intValue(Object raw, int fallback, String path) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(path + ": expected a whole number, got '" + raw + "'");
        }
    }

    private static boolean booleanValue(Object raw, String path) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = raw.toString().trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException(path + ": expected true or false, got '" + raw + "'");
    }
}
