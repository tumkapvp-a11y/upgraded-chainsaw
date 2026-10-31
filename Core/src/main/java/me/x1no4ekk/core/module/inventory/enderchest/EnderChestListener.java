package me.x1no4ekk.core.module.inventory.enderchest;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

/**
 * Storage slots of an ender chest page behave like a normal chest; only the navigation row is protected.
 * Read-only viewers (other players' chests without the modify permission) cannot change anything.
 * Also replaces opening the vanilla ender chest block when enabled.
 */
public final class EnderChestListener implements Listener {

    private final EnderChestService service;

    public EnderChestListener(EnderChestService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof EnderChestHolder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        EnderChestSession session = holder.session();
        if (session.isEnded()) {
            event.setCancelled(true);
            return;
        }
        // Covers plain clicks, number-key and off-hand swaps aimed at the navigation row.
        if (EnderChestService.isNavigationSlot(top, event.getRawSlot())) {
            event.setCancelled(true);
            service.navigate(player, holder, event.getRawSlot() - (top.getSize() - 9));
            return;
        }
        if (!service.canEdit(player, session) || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof EnderChestHolder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean editable = !holder.session().isEnded() && service.canEdit(player, holder.session());
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && (!editable || EnderChestService.isNavigationSlot(top, rawSlot))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpenGuard(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder(false) instanceof EnderChestHolder holder && holder.session().isEnded()) {
            event.setCancelled(true);
            return;
        }
        // While a vanilla import is being written, the owner's vanilla ender chest must stay untouched.
        if (inventory.getType() == InventoryType.ENDER_CHEST && service.isImporting(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof EnderChestHolder holder) {
            service.viewerOpened(event.getPlayer().getUniqueId(), holder.session());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof EnderChestHolder holder && event.getPlayer() instanceof Player player) {
            service.viewerClosed(player, holder.session(), event.getReason());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || block.getType() != Material.ENDER_CHEST) {
            return;
        }
        // Respect protection plugins that already denied using the block.
        if (event.useInteractedBlock() == Event.Result.DENY || !service.settings().replaceVanilla()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(EnderChestService.PERMISSION)) {
            return;
        }
        PlayerInventory hands = player.getInventory();
        // Vanilla does not open the chest when sneaking with an item or when the lid is blocked.
        if (player.isSneaking() && (!hands.getItemInMainHand().isEmpty() || !hands.getItemInOffHand().isEmpty())) {
            return;
        }
        if (block.getRelative(BlockFace.UP).getType().isOccluding()) {
            return;
        }
        // Deny both hands so the off-hand interaction cannot open the vanilla chest either.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        player.incrementStatistic(Statistic.ENDERCHEST_OPENED);
        service.open(player, player.getUniqueId(), player.getName());
    }
}
