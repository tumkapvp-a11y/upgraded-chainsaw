package dev.applesub.gui;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TrashMenu extends Menu {

    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int TOGGLE_SLOT = 53;

    private record Entry(Material material, boolean standard) {
    }

    private List<Entry> entries = List.of();
    private int page;
    private int pages = 1;

    public TrashMenu(AppleSubPlugin plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.settings().menuText("trash.title"));
    }

    @Override
    protected void render() {
        Inventory inventory = getInventory();
        Settings settings = plugin.settings();
        PlayerData data = plugin.dataManager().get(viewer.getUniqueId());
        inventory.clear();
        applyLayout(settings.trashBar(), PAGE_SIZE);
        if (data == null) {
            return;
        }

        List<Entry> list = new ArrayList<>();
        for (Material material : settings.defaultTrash()) {
            list.add(new Entry(material, true));
        }
        data.customTrash().stream()
                .filter(material -> !settings.isDefaultTrash(material))
                .sorted(Comparator.comparing(Material::name))
                .forEach(material -> list.add(new Entry(material, false)));
        entries = list;
        pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(page, 0, pages - 1);

        List<net.kyori.adventure.text.Component> standardLore = settings.menuLore("trash.default-entry-lore");
        List<net.kyori.adventure.text.Component> customLore = settings.menuLore("trash.custom-entry-lore");
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            inventory.setItem(i, Items.create(entry.material(), null, entry.standard() ? standardLore : customLore));
        }

        TagResolver[] placeholders = {
                Placeholder.unparsed("count", String.valueOf(data.customTrash().size())),
                Placeholder.unparsed("max", String.valueOf(settings.maxCustomTrash())),
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages)),
                Placeholder.component("state", settings.state(data.isEnabled(Feature.ANTI_TRASH)))
        };
        inventory.setItem(BACK_SLOT, Items.create(Material.ARROW, settings.menuText("trash.back.name"), null));
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, Items.create(Material.SPECTRAL_ARROW, settings.menuText("trash.previous.name"), null));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, Items.create(Material.SPECTRAL_ARROW, settings.menuText("trash.next.name"), null));
        }
        inventory.setItem(INFO_SLOT, Items.create(Material.BOOK,
                settings.menuText("trash.info.name", placeholders), settings.menuLore("trash.info.lore", placeholders)));
        boolean enabled = data.isEnabled(Feature.ANTI_TRASH);
        inventory.setItem(TOGGLE_SLOT, Items.glow(Items.create(Material.HOPPER,
                settings.menuText("trash.toggle.name", placeholders), settings.menuLore("trash.toggle.lore", placeholders)), enabled));
    }

    @Override
    public void onTopClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            click();
            later(() -> new MainMenu(plugin, viewer).open());
            return;
        }
        PlayerData data = activeData();
        if (data == null) {
            return;
        }
        Settings settings = plugin.settings();
        if (slot < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot;
            if (index >= entries.size()) {
                return;
            }
            Entry entry = entries.get(index);
            if (entry.standard()) {
                settings.actionBar(viewer, "trash-default-locked");
                settings.sound(viewer, "error");
                return;
            }
            data.customTrash().remove(entry.material());
            plugin.dataManager().removeTrash(data, entry.material());
            settings.send(viewer, "trash-removed", itemName(entry.material()));
            settings.sound(viewer, "toggle-off");
            render();
        } else if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            click();
            render();
        } else if (slot == NEXT_SLOT && page < pages - 1) {
            page++;
            click();
            render();
        } else if (slot == TOGGLE_SLOT) {
            plugin.service().toggle(viewer, data, Feature.ANTI_TRASH);
            render();
        }
    }

    @Override
    public void onBottomClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.isEmpty()) {
            return;
        }
        PlayerData data = activeData();
        if (data == null) {
            return;
        }
        Settings settings = plugin.settings();
        Material material = item.getType();
        if (settings.isDefaultTrash(material)) {
            settings.send(viewer, "trash-already-default", itemName(material));
            settings.sound(viewer, "error");
        } else if (data.customTrash().contains(material)) {
            settings.send(viewer, "trash-already-added", itemName(material));
            settings.sound(viewer, "error");
        } else if (data.customTrash().size() >= settings.maxCustomTrash()) {
            settings.send(viewer, "trash-limit", Placeholder.unparsed("max", String.valueOf(settings.maxCustomTrash())));
            settings.sound(viewer, "error");
        } else {
            data.customTrash().add(material);
            plugin.dataManager().addTrash(data, material);
            settings.send(viewer, "trash-added", itemName(material));
            settings.sound(viewer, "toggle-on");
            // Jump to the page with the new item.
            render();
            int index = entries.indexOf(new Entry(material, false));
            if (index >= 0 && index / PAGE_SIZE != page) {
                page = index / PAGE_SIZE;
                render();
            }
        }
    }

    private static TagResolver itemName(Material material) {
        return Placeholder.component("item", ItemStack.of(material).effectiveName());
    }
}
