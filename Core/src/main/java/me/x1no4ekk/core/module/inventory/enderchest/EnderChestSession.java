package me.x1no4ekk.core.module.inventory.enderchest;

import me.x1no4ekk.core.util.ItemBuilder;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One owner's ender chest while at least one player views it. Every viewer of a page shares the same
 * {@link Inventory}, so the owner and an administrator always see and edit the same items (no duplication).
 * Pages are materialized lazily from the loaded data; their serialized state at that moment is the baseline
 * used to save only pages that actually changed. Main thread only.
 */
public final class EnderChestSession {

    private final UUID ownerId;
    private final String ownerName;
    private final EnderChestSettings settings;
    private final NamespacedKey navigationKey;
    /** No rows existed: page 1 is written on close even if empty, marking the chest as initialized. */
    private final boolean fresh;
    private final Map<Integer, ItemStack[]> stored;
    private final Map<Integer, byte[]> baseline = new HashMap<>();
    private final NavigableMap<Integer, Inventory> pages = new TreeMap<>();
    private final Set<UUID> viewers = new HashSet<>();
    private int pageCount;
    private boolean ended;

    EnderChestSession(UUID ownerId, String ownerName, EnderChestSettings settings, NamespacedKey navigationKey,
                      Map<Integer, ItemStack[]> stored, boolean fresh, int pageCount) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.settings = settings;
        this.navigationKey = navigationKey;
        this.stored = new HashMap<>(stored);
        this.fresh = fresh;
        this.pageCount = Math.max(1, pageCount);
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    /**
     * Settings snapshot taken when the session was created; layout stays consistent across a /core reload.
     */
    public EnderChestSettings settings() {
        return settings;
    }

    public int pageCount() {
        return pageCount;
    }

    public boolean isEnded() {
        return ended;
    }

    // ---------------------------------------------------------------- viewers

    void addViewer(UUID viewerId) {
        viewers.add(viewerId);
    }

    /**
     * @return {@code true} if the player was a viewer
     */
    boolean removeViewer(UUID viewerId) {
        return viewers.remove(viewerId);
    }

    void retainViewers(Predicate<UUID> stillViewing) {
        viewers.removeIf(stillViewing.negate());
    }

    boolean hasViewers() {
        return !viewers.isEmpty();
    }

    // ---------------------------------------------------------------- pages

    /**
     * @param number 1-based page number
     */
    Inventory page(int number) {
        Inventory existing = pages.get(number);
        if (existing != null) {
            return existing;
        }
        ItemStack[] contents = stored.remove(number);
        int storageSize = storageSize(contents);
        EnderChestHolder holder = new EnderChestHolder(this, number);
        Inventory inventory = Bukkit.createInventory(holder, storageSize + 9, Text.parse(settings.title(), resolvers(number)));
        holder.bind(inventory);
        if (contents != null) {
            for (int slot = 0; slot < contents.length && slot < storageSize; slot++) {
                inventory.setItem(slot, contents[slot]);
            }
            baseline.put(number, ItemStack.serializeItemsAsBytes(storage(inventory)));
        }
        renderNavigation(inventory, number);
        pages.put(number, inventory);
        return inventory;
    }

    /**
     * Grows the page count (e.g. the owner opened a chest an administrator loaded while they were offline).
     */
    void ensurePageCount(int count) {
        if (count <= pageCount) {
            return;
        }
        pageCount = count;
        pages.forEach((number, inventory) -> renderNavigation(inventory, number));
    }

    /**
     * Configured storage, enlarged if stored data uses more rows (storage rows were reduced in the config):
     * items are never cut off.
     */
    private int storageSize(ItemStack @Nullable [] contents) {
        int size = settings.storageSize();
        if (contents != null) {
            int maxSize = EnderChestSettings.MAX_STORAGE_ROWS * 9;
            for (int slot = Math.min(contents.length, maxSize) - 1; slot >= size; slot--) {
                if (!isEmpty(contents[slot])) {
                    return (slot / 9 + 1) * 9;
                }
            }
        }
        return size;
    }

    private TagResolver resolvers(int number) {
        return TagResolver.resolver(
                Placeholder.unparsed("owner", ownerName),
                Placeholder.unparsed("page", String.valueOf(number)),
                Placeholder.unparsed("pages", String.valueOf(pageCount)),
                Placeholder.unparsed("previous", String.valueOf(number - 1)),
                Placeholder.unparsed("next", String.valueOf(number + 1))
        );
    }

    /**
     * The bottom row is always completely filled with tagged items: vanilla shift-clicks can then never move
     * anything into it, and tagged items never stack with real ones.
     */
    private void renderNavigation(Inventory inventory, int number) {
        int start = inventory.getSize() - 9;
        TagResolver resolvers = resolvers(number);
        ItemStack filler = tag(ItemBuilder.of(settings.filler()).hideTooltip().build());
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(start + slot, filler);
        }
        if (number > 1) {
            inventory.setItem(start + settings.previous().slot(), tag(settings.previous().build(resolvers)));
        }
        if (number < pageCount) {
            inventory.setItem(start + settings.next().slot(), tag(settings.next().build(resolvers)));
        }
        inventory.setItem(start + settings.info().slot(), tag(settings.info().build(resolvers)));
    }

    private ItemStack tag(ItemStack item) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(navigationKey, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    // ---------------------------------------------------------------- ending

    /**
     * Ends the session: no further edits are accepted, stray views are closed.
     *
     * @return serialized contents of every page that changed, keyed by page number
     */
    Map<Integer, byte[]> end() {
        ended = true;
        Map<Integer, byte[]> changes = new TreeMap<>();
        for (Map.Entry<Integer, Inventory> entry : pages.entrySet()) {
            int number = entry.getKey();
            ItemStack[] contents = storage(entry.getValue());
            byte[] bytes = ItemStack.serializeItemsAsBytes(contents);
            byte[] before = baseline.get(number);
            if (before == null) {
                if (isEmpty(contents) && !(fresh && number == 1)) {
                    continue;
                }
            } else if (Arrays.equals(before, bytes)) {
                continue;
            }
            changes.put(number, bytes);
        }
        for (Inventory inventory : pages.values()) {
            for (HumanEntity viewer : List.copyOf(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
        viewers.clear();
        return changes;
    }

    private static ItemStack[] storage(Inventory inventory) {
        return Arrays.copyOf(inventory.getContents(), inventory.getSize() - 9);
    }

    static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.isEmpty();
    }

    static boolean isEmpty(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (!isEmpty(item)) {
                return false;
            }
        }
        return true;
    }
}
