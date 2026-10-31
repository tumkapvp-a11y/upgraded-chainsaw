package me.x1no4ekk.core.horn;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.x1no4ekk.core.config.Settings;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The "Teleport Horn": a GOAT_HORN identified by a PersistentDataContainer marker. It is the only way
 * for players to teleport to their homes.
 */
public final class TeleportHorn {

    private final Logger logger;
    private final Supplier<Settings> settings;
    private final NamespacedKey markerKey;
    private final NamespacedKey recipeKey;

    public TeleportHorn(Plugin plugin, Supplier<Settings> settings) {
        this.logger = plugin.getLogger();
        this.settings = settings;
        this.markerKey = new NamespacedKey(plugin, "teleport_horn");
        this.recipeKey = new NamespacedKey(plugin, "teleport_horn");
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    public ItemStack create(int amount) {
        Settings.HornSettings config = settings.get().horn();
        ItemStack item = new ItemStack(Material.GOAT_HORN, Math.clamp(amount, 1, Material.GOAT_HORN.getMaxStackSize()));
        MusicInstrument instrument = resolveInstrument(config.instrument());
        item.editMeta(meta -> {
            meta.displayName(Text.item(config.name()));
            meta.lore(config.lore().isEmpty() ? null : Text.itemLines(config.lore()));
            if (config.glint()) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (instrument != null && meta instanceof MusicInstrumentMeta instrumentMeta) {
                instrumentMeta.setInstrument(instrument);
            }
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public boolean isHorn(ItemStack item) {
        return item != null
                && item.getType() == Material.GOAT_HORN
                && item.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * (Re)registers the crafting recipe from the current settings.
     */
    public void registerRecipe() {
        unregisterRecipe();
        Settings.HornSettings config = settings.get().horn();
        if (!config.recipeEnabled()) {
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create(1));
        recipe.shape(config.shape().toArray(String[]::new));
        for (Map.Entry<Character, Material> ingredient : config.ingredients().entrySet()) {
            recipe.setIngredient(ingredient.getKey(), ingredient.getValue());
        }
        Bukkit.addRecipe(recipe, true);
    }

    public void unregisterRecipe() {
        if (Bukkit.getRecipe(recipeKey) != null) {
            Bukkit.removeRecipe(recipeKey, true);
        }
    }

    public void discoverRecipe(Player player) {
        Settings.HornSettings config = settings.get().horn();
        if (config.recipeEnabled() && config.discoverOnJoin() && player.hasPermission("core.horn.craft")) {
            player.discoverRecipe(recipeKey);
        }
    }

    /**
     * Plays the configured horn sound, following the player.
     */
    public void playSound(Player player) {
        Settings.HornSettings config = settings.get().horn();
        Key key;
        try {
            key = Key.key(config.soundKey());
        } catch (InvalidKeyException e) {
            logger.warning("Invalid teleport-horn.sound.key: " + config.soundKey());
            return;
        }
        player.playSound(Sound.sound(key, Sound.Source.PLAYER, config.soundVolume(), config.soundPitch()), Sound.Emitter.self());
    }

    public int cooldownTicks() {
        return settings.get().horn().cooldownSeconds() * 20;
    }

    private MusicInstrument resolveInstrument(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            MusicInstrument instrument = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.INSTRUMENT)
                    .get(Key.key(raw));
            if (instrument == null) {
                logger.warning("Unknown teleport-horn.instrument: " + raw);
            }
            return instrument;
        } catch (InvalidKeyException e) {
            logger.warning("Invalid teleport-horn.instrument: " + raw);
            return null;
        }
    }
}
