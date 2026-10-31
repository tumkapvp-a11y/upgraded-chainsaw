package me.x1no4ekk.core.gui;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.config.Settings;
import me.x1no4ekk.core.home.Home;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.ItemBuilder;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Home list. In "own" mode (opened with the Teleport Horn) a click plays the horn and teleports the viewer;
 * in "other" mode (/playerhome) an administrator teleports to another player's home.
 * <p>
 * Holds only immutable data: a snapshot of homes, the owner's UUID and name.
 */
public final class HomeGui extends Gui {

    private final CorePlugin plugin;
    private final Settings.HomeGuiSettings config;
    private final UUID ownerId;
    private final String ownerName;
    private final boolean own;
    private final List<Home> homes;
    private int page;

    private HomeGui(CorePlugin plugin, Settings.HomeGuiSettings config, UUID ownerId, String ownerName,
                    boolean own, List<Home> homes) {
        super(config.rows(), own
                ? Text.parse(config.title())
                : Text.parse(config.titleOther(), Placeholder.unparsed("player", ownerName)));
        this.plugin = plugin;
        this.config = config;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.own = own;
        this.homes = List.copyOf(homes);
        render();
    }

    /**
     * Menu of the viewer's own homes (Teleport Horn).
     */
    public static HomeGui own(CorePlugin plugin, User user) {
        return new HomeGui(plugin, plugin.settings().homeGui(), user.getUuid(), user.getName(), true, user.getHomesSorted());
    }

    /**
     * Menu of another player's homes (/playerhome).
     */
    public static HomeGui other(CorePlugin plugin, UUID ownerId, String ownerName, List<Home> homes) {
        List<Home> sorted = homes.stream()
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()))
                .toList();
        return new HomeGui(plugin, plugin.settings().homeGui(), ownerId, ownerName, false, sorted);
    }

    public boolean isOwn() {
        return own;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    private int pageCount() {
        return Math.max(1, (homes.size() + config.contentSlots().size() - 1) / config.contentSlots().size());
    }

    private void render() {
        clear();
        int size = inventory.getSize();
        List<Integer> contentSlots = config.contentSlots();

        if (config.fillerEnabled()) {
            Set<Integer> content = new HashSet<>(contentSlots);
            ItemStack filler = ItemBuilder.of(config.fillerMaterial()).hideTooltip().build();
            for (int slot = 0; slot < size; slot++) {
                if (!content.contains(slot)) {
                    setItem(slot, filler);
                }
            }
        }

        if (homes.isEmpty()) {
            Settings.ButtonSettings empty = config.emptyItem();
            setItem(empty.slot(), button(empty));
        } else {
            int perPage = contentSlots.size();
            int start = page * perPage;
            int end = Math.min(homes.size(), start + perPage);
            for (int i = start; i < end; i++) {
                Home home = homes.get(i);
                setItem(contentSlots.get(i - start), homeIcon(home), (player, event) -> onHomeClick(player, home));
            }
        }

        if (page > 0) {
            setItem(config.previousPage().slot(), button(config.previousPage()), (player, event) -> {
                page--;
                render();
            });
        }
        if (page < pageCount() - 1) {
            setItem(config.nextPage().slot(), button(config.nextPage()), (player, event) -> {
                page++;
                render();
            });
        }
        setItem(config.close().slot(), button(config.close()), (player, event) -> {
            UUID viewerId = player.getUniqueId();
            // Inventory must not be closed from inside InventoryClickEvent.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    viewer.closeInventory();
                }
            });
        });
    }

    private ItemStack homeIcon(Home home) {
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.unparsed("home", home.name()),
                Placeholder.unparsed("world", home.world()),
                Placeholder.unparsed("x", String.valueOf((int) Math.floor(home.x()))),
                Placeholder.unparsed("y", String.valueOf((int) Math.floor(home.y()))),
                Placeholder.unparsed("z", String.valueOf((int) Math.floor(home.z())))
        );
        Material material = config.homeMaterials().getOrDefault(home.environment(), Material.COMPASS);
        return ItemBuilder.of(material)
                .name(config.homeName(), placeholders)
                .lore(config.homeLore(), placeholders)
                .build();
    }

    private static ItemStack button(Settings.ButtonSettings button) {
        return ItemBuilder.of(button.material())
                .name(button.name())
                .lore(button.lore())
                .build();
    }

    private void onHomeClick(Player clicker, Home home) {
        UUID viewerId = clicker.getUniqueId();
        // Close + teleport on the next tick: closing inside InventoryClickEvent is unsafe.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                return;
            }
            viewer.closeInventory();
            if (own) {
                plugin.homeTeleporter().teleportWithHorn(viewer, home.name());
            } else {
                plugin.homeTeleporter().teleportToOther(viewer, ownerName, home);
            }
        });
    }
}
