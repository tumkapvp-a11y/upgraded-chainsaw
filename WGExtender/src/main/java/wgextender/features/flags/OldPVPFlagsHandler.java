package wgextender.features.flags;

import com.google.common.base.Function;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import wgextender.WGExtender;
import wgextender.utils.WGRegionUtils;

/**
 * oldpvp-noshieldblock and oldpvp-nobow flags. The attack speed flag lives in {@link OldPVPAttackSpeedHandler}.
 */
@SuppressWarnings({"deprecation", "removal"}) // DamageModifier is deprecated but still the only way to drop shield blocking
public final class OldPVPFlagsHandler implements Listener {

    // Same order as the server applies the modifiers.
    private static final DamageModifier[] BEFORE_BLOCKING = {
        DamageModifier.BASE, DamageModifier.INVULNERABILITY_REDUCTION, DamageModifier.FREEZING, DamageModifier.HARD_HAT
    };
    private static final DamageModifier[] AFTER_BLOCKING = {
        DamageModifier.ARMOR, DamageModifier.RESISTANCE, DamageModifier.MAGIC, DamageModifier.ABSORPTION
    };

    private final WGExtender plugin;
    private final Field functionsField;

    public OldPVPFlagsHandler(WGExtender plugin) {
        this.plugin = plugin;
        this.functionsField = findModifierFunctionsField(plugin);
    }

    private static Field findModifierFunctionsField(WGExtender plugin) {
        try {
            Field field = EntityDamageEvent.class.getDeclaredField("modifierFunctions");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "oldpvp-noshieldblock flag is not supported on this server version", e);
            return null;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (functionsField == null || !(event.getEntity() instanceof Player player) || !player.isBlocking()) {
            return;
        }
        if (!event.isApplicable(DamageModifier.BLOCKING)
            || !WGRegionUtils.isFlagTrue(player.getLocation(), WGExtenderFlags.OLDPVP_NOSHIELDBLOCK)) {
            return;
        }

        Map<DamageModifier, ? extends Function<? super Double, Double>> functions;
        try {
            @SuppressWarnings("unchecked")
            Map<DamageModifier, ? extends Function<? super Double, Double>> value =
                (Map<DamageModifier, ? extends Function<? super Double, Double>>) functionsField.get(event);
            functions = value;
        } catch (IllegalAccessException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Unable to recalculate blocking damage", e);
            return;
        }

        double damage = 0;
        for (DamageModifier modifier : BEFORE_BLOCKING) {
            damage += event.getDamage(modifier);
        }
        event.setDamage(DamageModifier.BLOCKING, 0);
        // Armor and other reductions were calculated from the blocked damage, recalculate them.
        for (DamageModifier modifier : AFTER_BLOCKING) {
            Function<? super Double, Double> function = functions.get(modifier);
            if (function == null || !event.isApplicable(modifier)) {
                continue;
            }
            double value = function.apply(damage);
            event.setDamage(modifier, value);
            damage += value;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND || !event.getAction().isRightClick()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.BOW
            && WGRegionUtils.isFlagTrue(event.getPlayer().getLocation(), WGExtenderFlags.OLDPVP_NOBOW)) {
            event.setCancelled(true);
        }
    }
}
