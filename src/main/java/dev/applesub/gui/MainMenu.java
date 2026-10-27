package dev.applesub.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.applesub.AppleSubPlugin;
import dev.applesub.config.Settings;
import dev.applesub.data.Feature;
import dev.applesub.data.PlayerData;
import dev.applesub.util.Items;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;

public final class MainMenu extends Menu {

    private static final int INFO_SLOT = 13;
    private static final int CLOSE_SLOT = 40;
    /** Feature icon slots; the status item sits one row below (+9). */
    private static final Map<Feature, Integer> ICON_SLOTS = Map.of(
            Feature.NIGHT_VISION, 19,
            Feature.ANTI_FIRE, 21,
            Feature.ANTI_TRASH, 23,
            Feature.ARMOR_NOTIFY, 25);
    private static final Map<Feature, Material> DEFAULT_ICONS = Map.of(
            Feature.NIGHT_VISION, Material.ENDER_EYE,
            Feature.ANTI_FIRE, Material.BLAZE_POWDER,
            Feature.ANTI_TRASH, Material.HOPPER,
            Feature.ARMOR_NOTIFY, Material.ANVIL);

    public MainMenu(AppleSubPlugin plugin, Player viewer) {
        super(plugin, viewer, 5, plugin.settings().menuText("main.title"));
    }

    @Override
    protected void render() {
        Inventory inventory = getInventory();
        Settings settings = plugin.settings();
        PlayerData data = plugin.dataManager().get(viewer.getUniqueId());
        inventory.clear();
        frame();
        if (data == null) {
            return;
        }

        ItemStack info = Items.create(Material.PLAYER_HEAD,
                settings.menuText("main.info.name", Placeholder.unparsed("player", viewer.getName())),
                settings.menuLore("main.info.lore",
                        Placeholder.unparsed("player", viewer.getName()),
                        Placeholder.unparsed("time", settings.formatRemaining(data.expiresAt())),
                        Placeholder.unparsed("date", settings.formatDate(data.expiresAt()))));
        PlayerProfile profile = viewer.getPlayerProfile();
        // Without textures (offline mode) Paper would query Mojang on every open.
        if (profile.hasTextures()) {
            info.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        }
        inventory.setItem(INFO_SLOT, info);

        for (Feature feature : Feature.values()) {
            boolean enabled = data.isEnabled(feature);
            String path = "main.features." + feature.key();
            TagResolver[] placeholders = {
                    Placeholder.component("state", settings.state(enabled)),
                    Placeholder.unparsed("count", String.valueOf(data.customTrash().size())),
                    Placeholder.unparsed("max", String.valueOf(settings.maxCustomTrash()))
            };
            ItemStack icon = Items.create(settings.menuMaterial(path + ".material", DEFAULT_ICONS.get(feature)),
                    settings.menuText(path + ".name", placeholders), settings.menuLore(path + ".lore", placeholders));
            int slot = ICON_SLOTS.get(feature);
            inventory.setItem(slot, Items.glow(icon, enabled));

            Material statusMaterial = enabled
                    ? settings.menuMaterial("status-on-material", Material.PINK_DYE)
                    : settings.menuMaterial("status-off-material", Material.GRAY_DYE);
            inventory.setItem(slot + 9, Items.create(statusMaterial, settings.state(enabled), settings.menuLore("status-lore")));
        }

        inventory.setItem(CLOSE_SLOT, Items.create(Material.BARRIER, settings.menuText("main.close.name"), null));
    }

    @Override
    public void onTopClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            click();
            later(viewer::closeInventory);
            return;
        }
        Feature feature = featureAt(slot);
        if (feature == null) {
            return;
        }
        PlayerData data = activeData();
        if (data == null) {
            return;
        }
        if (feature == Feature.ANTI_TRASH && slot == ICON_SLOTS.get(feature) && event.isRightClick()) {
            click();
            later(() -> new TrashMenu(plugin, viewer).open());
            return;
        }
        plugin.service().toggle(viewer, data, feature);
        render();
    }

    private static Feature featureAt(int slot) {
        for (Map.Entry<Feature, Integer> entry : ICON_SLOTS.entrySet()) {
            if (slot == entry.getValue() || slot == entry.getValue() + 9) {
                return entry.getKey();
            }
        }
        return null;
    }
}
