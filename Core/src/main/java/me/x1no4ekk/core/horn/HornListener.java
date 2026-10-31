package me.x1no4ekk.core.horn;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.gui.HomeGui;
import me.x1no4ekk.core.user.User;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Recipe;

import java.util.EnumSet;
import java.util.Set;

/**
 * Right-click with the Teleport Horn opens the home menu instead of playing the horn.
 */
public final class HornListener implements Listener {

    private static final Set<Material> INTERACTABLE = EnumSet.of(
            Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.GRINDSTONE, Material.LOOM,
            Material.SMITHING_TABLE, Material.STONECUTTER, Material.ENCHANTING_TABLE, Material.CRAFTER,
            Material.NOTE_BLOCK, Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR,
            Material.RESPAWN_ANCHOR, Material.CAKE, Material.FLOWER_POT, Material.COMPOSTER, Material.CAULDRON,
            Material.WATER_CAULDRON, Material.LAVA_CAULDRON, Material.POWDER_SNOW_CAULDRON, Material.BELL);

    private final CorePlugin plugin;
    private final TeleportHorn horn;

    public HornListener(CorePlugin plugin, TeleportHorn horn) {
        this.plugin = plugin;
        this.horn = horn;
    }

    // Not ignoreCancelled: Bukkit marks RIGHT_CLICK_AIR interactions as cancelled by default.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!horn.isHorn(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();

        // Never let the horn play its vanilla sound.
        event.setUseItemInHand(Event.Result.DENY);

        // A horn in both hands: only the main-hand interaction opens the menu.
        if (event.getHand() == EquipmentSlot.OFF_HAND && horn.isHorn(player.getInventory().getItemInMainHand())) {
            return;
        }
        // Keep vanilla block interaction (doors, chests...) unless the player is sneaking.
        Block block = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && block != null && isInteractable(block) && !player.isSneaking()) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);

        // The client may send a second use packet for the same click; don't reopen.
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof HomeGui gui && gui.isOwn()) {
            return;
        }
        if (!player.hasPermission("core.horn.use")) {
            plugin.messages().send(player, "horn.no-permission");
            return;
        }
        User user = plugin.userManager().getUser(player);
        if (user == null) {
            plugin.messages().send(player, "general.data-not-loaded");
            return;
        }
        if (plugin.homeTeleporter().checkCooldown(player)) {
            return;
        }
        HomeGui.own(plugin, user).open(player);
    }

    /**
     * Blocks whose right-click action should win over the horn (Material#isInteractable is deprecated).
     */
    private static boolean isInteractable(Block block) {
        if (block.getState(false) instanceof TileState) {
            return true; // chests, furnaces, ender chests, signs, beacons...
        }
        BlockData data = block.getBlockData();
        if (data instanceof Openable || data instanceof Switch || data instanceof Bed) {
            return true; // doors, trapdoors, gates, levers, buttons, beds
        }
        return INTERACTABLE.contains(block.getType()) || Tag.ANVIL.isTagged(block.getType());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().equals(horn.recipeKey())) {
            return;
        }
        HumanEntity viewer = event.getView().getPlayer();
        if (!viewer.hasPermission("core.horn.craft")) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        horn.discoverRecipe(event.getPlayer());
    }
}
