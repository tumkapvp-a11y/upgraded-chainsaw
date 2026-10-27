package dev.applesub.feature;

import dev.applesub.AppleSubPlugin;
import dev.applesub.config.Settings;
import dev.applesub.data.Feature;
import dev.applesub.data.PlayerData;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Warns (with an anvil sound) when worn armor is about to break, and when it breaks. */
public final class ArmorNotifyListener implements Listener {

    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final AppleSubPlugin plugin;
    private final Map<UUID, Map<EquipmentSlot, Long>> lastWarning = new HashMap<>();

    public ArmorNotifyListener(AppleSubPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) {
            return;
        }
        ItemStack item = event.getItem();
        EquipmentSlot slot = wornSlot(player, item);
        if (slot == null || !(item.getItemMeta() instanceof Damageable meta)) {
            return;
        }
        int max = meta.hasMaxDamage() ? meta.getMaxDamage() : item.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        int remaining = max - meta.getDamage() - event.getDamage();
        Settings settings = plugin.settings();
        // remaining <= 0 means the item breaks now; PlayerItemBreakEvent handles that.
        if (remaining <= 0 || remaining * 100L > (long) max * settings.armorThresholdPercent()) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<EquipmentSlot, Long> slots = lastWarning.computeIfAbsent(player.getUniqueId(), uuid -> new EnumMap<>(EquipmentSlot.class));
        Long last = slots.get(slot);
        if (last != null && now - last < settings.armorCooldownMillis()) {
            return;
        }
        slots.put(slot, now);

        TagResolver[] placeholders = {
                Placeholder.unparsed("slot", settings.slotName(slot)),
                Placeholder.component("item", item.effectiveName()),
                Placeholder.unparsed("durability", String.valueOf(remaining)),
                Placeholder.unparsed("max", String.valueOf(max)),
                Placeholder.unparsed("percent", String.valueOf(remaining * 100 / max))
        };
        settings.send(player, "armor-low", placeholders);
        if (settings.armorTitle()) {
            settings.title(player, "armor-low-title", "armor-low-subtitle", placeholders);
        }
        settings.sound(player, "armor-low");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) {
            return;
        }
        ItemStack item = event.getBrokenItem();
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        if (!ARMOR_SLOTS.contains(slot)) {
            return;
        }
        Map<EquipmentSlot, Long> slots = lastWarning.get(player.getUniqueId());
        if (slots != null) {
            slots.remove(slot);
        }
        Settings settings = plugin.settings();
        TagResolver[] placeholders = {
                Placeholder.unparsed("slot", settings.slotName(slot)),
                Placeholder.component("item", item.effectiveName())
        };
        settings.send(player, "armor-broken", placeholders);
        if (settings.armorTitle()) {
            settings.title(player, "armor-broken-title", "armor-broken-subtitle", placeholders);
        }
        settings.sound(player, "armor-broken");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    private boolean isEnabled(Player player) {
        PlayerData data = plugin.dataManager().get(player.getUniqueId());
        return data != null && data.isActive(Feature.ARMOR_NOTIFY);
    }

    private static EquipmentSlot wornSlot(Player player, ItemStack item) {
        EntityEquipment equipment = player.getEquipment();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (item.equals(equipment.getItem(slot))) {
                return slot;
            }
        }
        return null;
    }
}
