package me.x1no4ekk.core.module.inventory.kit;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Kit rules: availability, claiming (permission, cooldown, price, free space), delivery with armor auto-equip,
 * cooldown persistence in {@code core_kit_cooldowns} and first-join kits.
 */
public final class KitService {

    public static final String USE_PERMISSION = "core.kit";
    public static final String ADMIN_PERMISSION = "core.kit.admin";

    private static final String UPSERT_COOLDOWN =
            "INSERT INTO core_kit_cooldowns (uuid, kit, last_used) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE last_used = VALUES(last_used)";
    private static final Set<EquipmentSlot> ARMOR_SLOTS =
            Set.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    /** Retries (one per second) while a joining player's data is still loading. */
    private static final int FIRST_JOIN_ATTEMPTS = 10;

    private final CorePlugin plugin;
    private volatile KitsConfig config;
    /** Pending first-join deliveries; main thread only, cleaned on quit. */
    private final Map<UUID, BukkitTask> firstJoinTasks = new HashMap<>();

    public enum GiveResult {
        GIVEN,
        /** Given, but part of the items was dropped at the player's feet. */
        DROPPED,
        NO_SPACE
    }

    public record KitState(KitStatus status, long remainingMillis) {
    }

    public KitService(CorePlugin plugin, KitsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public KitsConfig config() {
        return config;
    }

    public void apply(KitsConfig config) {
        this.config = config;
    }

    // ---------------------------------------------------------------- availability

    public KitState state(Player player, @Nullable User user, Kit kit) {
        if (!player.hasPermission(kit.permission())) {
            return new KitState(KitStatus.NO_PERMISSION, 0);
        }
        Long lastUsed = user == null ? null : user.getKitLastUsed(kit.id());
        if (lastUsed != null) {
            if (kit.oneTime()) {
                return new KitState(KitStatus.CLAIMED, 0);
            }
            if (kit.cooldownSeconds() > 0) {
                long remaining = lastUsed + kit.cooldownSeconds() * 1000L - System.currentTimeMillis();
                if (remaining > 0) {
                    return new KitState(KitStatus.COOLDOWN, remaining);
                }
            }
        }
        return new KitState(KitStatus.AVAILABLE, 0);
    }

    /**
     * Cooldown description for menus and messages ("1д", "одноразовый", "нет").
     */
    public Component cooldownText(long cooldownSeconds) {
        KitsConfig.GuiSettings gui = config.gui();
        if (cooldownSeconds < 0) {
            return Text.parse(gui.cooldownOneTime());
        }
        if (cooldownSeconds == 0) {
            return Text.parse(gui.cooldownNone());
        }
        return Component.text(Durations.format(cooldownSeconds));
    }

    public Component priceText(double price) {
        KitsConfig.GuiSettings gui = config.gui();
        if (price <= 0) {
            return Text.parse(gui.priceFree());
        }
        return Text.parse(gui.pricePaid(), Placeholder.unparsed("amount", formatAmount(price)));
    }

    public static String formatAmount(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------- claiming

    /**
     * Claims a kit with every check (permission, cooldown, price, free space) and replies to the player.
     *
     * @return {@code true} if the kit was given
     */
    public boolean claim(Player player, Kit kit) {
        User user = plugin.userManager().getUser(player);
        if (user == null) {
            plugin.messages().send(player, "general.data-not-loaded");
            return false;
        }
        KitState state = state(player, user, kit);
        switch (state.status()) {
            case NO_PERMISSION -> {
                plugin.messages().send(player, "kit.no-permission", Placeholder.component("kit", kit.displayComponent()));
                return false;
            }
            case CLAIMED -> {
                plugin.messages().send(player, "kit.already-claimed", Placeholder.component("kit", kit.displayComponent()));
                return false;
            }
            case COOLDOWN -> {
                plugin.messages().send(player, "kit.cooldown",
                        Placeholder.component("kit", kit.displayComponent()),
                        Placeholder.unparsed("time", Durations.formatMillis(state.remainingMillis())));
                return false;
            }
            case AVAILABLE -> {
            }
        }

        double price = kit.price();
        if (price > 0) {
            if (!plugin.vault().hasEconomy()) {
                plugin.messages().send(player, "kit.economy-unavailable");
                return false;
            }
            if (!plugin.vault().has(player, price)) {
                sendNotEnoughMoney(player, kit, price);
                return false;
            }
        }

        Delivery delivery = plan(player, kit);
        // Space is checked before money is taken, so a refused kit never costs anything.
        if (config.fullInventory() == KitsConfig.FullInventoryMode.DENY && !delivery.fits(player)) {
            plugin.messages().send(player, "kit.inventory-full", Placeholder.component("kit", kit.displayComponent()));
            return false;
        }
        if (price > 0 && !plugin.vault().withdraw(player, price)) {
            sendNotEnoughMoney(player, kit, price);
            return false;
        }

        boolean dropped = delivery.apply(player);
        recordUse(user, kit.id());
        if (price > 0) {
            plugin.messages().send(player, "kit.claimed-paid",
                    Placeholder.component("kit", kit.displayComponent()),
                    Placeholder.unparsed("price", formatAmount(price)));
        } else {
            plugin.messages().send(player, "kit.claimed", Placeholder.component("kit", kit.displayComponent()));
        }
        if (dropped) {
            plugin.messages().send(player, "kit.dropped");
        }
        return true;
    }

    private void sendNotEnoughMoney(Player player, Kit kit, double price) {
        plugin.messages().send(player, "kit.not-enough-money",
                Placeholder.component("kit", kit.displayComponent()),
                Placeholder.unparsed("price", formatAmount(price)),
                Placeholder.unparsed("balance", formatAmount(plugin.vault().balance(player))));
    }

    /**
     * Administrative delivery: ignores permission, cooldown and price; does not touch the cooldown.
     */
    public GiveResult give(Player target, Kit kit) {
        Delivery delivery = plan(target, kit);
        if (config.fullInventory() == KitsConfig.FullInventoryMode.DENY && !delivery.fits(target)) {
            return GiveResult.NO_SPACE;
        }
        return delivery.apply(target) ? GiveResult.DROPPED : GiveResult.GIVEN;
    }

    private void recordUse(User user, String kitId) {
        long now = System.currentTimeMillis();
        user.setKitLastUsed(kitId, now);
        UUID uuid = user.getUuid();
        plugin.database().update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_COOLDOWN)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, kitId);
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        }).whenCompleteAsync((ignored, error) -> {
            if (error == null) {
                return;
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to save cooldown of kit '" + kitId + "' for " + uuid, error);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, "general.database-error");
            }
        }, plugin.mainThread());
    }

    // ---------------------------------------------------------------- delivery

    /**
     * Items split into armor that goes straight into empty armor slots and the rest for the main inventory.
     */
    private record Delivery(Map<EquipmentSlot, ItemStack> equip, List<ItemStack> rest) {

        /**
         * Simulates adding {@link #rest} to a copy of the storage slots.
         */
        boolean fits(Player player) {
            if (rest.isEmpty()) {
                return true;
            }
            ItemStack[] storage = player.getInventory().getStorageContents();
            Inventory simulation = Bukkit.createInventory(null, 36);
            for (int slot = 0; slot < storage.length && slot < simulation.getSize(); slot++) {
                ItemStack item = storage[slot];
                simulation.setItem(slot, item == null ? null : item.clone());
            }
            ItemStack[] copies = rest.stream().map(ItemStack::clone).toArray(ItemStack[]::new);
            return simulation.addItem(copies).isEmpty();
        }

        /**
         * @return {@code true} if something did not fit and was dropped at the player's feet
         */
        boolean apply(Player player) {
            PlayerInventory inventory = player.getInventory();
            equip.forEach(inventory::setItem);
            if (rest.isEmpty()) {
                return false;
            }
            return !player.give(rest, true).drops().isEmpty();
        }
    }

    private Delivery plan(Player player, Kit kit) {
        PlayerInventory inventory = player.getInventory();
        Map<EquipmentSlot, ItemStack> equip = new EnumMap<>(EquipmentSlot.class);
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack item : kit.copyItems()) {
            EquipmentSlot slot = kit.autoEquip() ? armorSlot(item) : null;
            if (slot != null && !equip.containsKey(slot) && isEmpty(inventory.getItem(slot))) {
                equip.put(slot, item);
            } else {
                rest.add(item);
            }
        }
        return new Delivery(equip, rest);
    }

    private static @Nullable EquipmentSlot armorSlot(ItemStack item) {
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) {
            return null;
        }
        EquipmentSlot slot = equippable.slot();
        return ARMOR_SLOTS.contains(slot) ? slot : null;
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.isEmpty();
    }

    // ---------------------------------------------------------------- first join

    /**
     * Gives first-join kits shortly after a brand-new player joins (data normally loads at pre-login;
     * the task retries for a few seconds if it is still loading).
     */
    public void scheduleFirstJoin(Player player) {
        KitsConfig current = config;
        if (current.kits().values().stream().noneMatch(Kit::firstJoin)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cancelFirstJoin(uuid);
        BukkitRunnable runnable = new BukkitRunnable() {
            private int attempts;

            @Override
            public void run() {
                Player online = Bukkit.getPlayer(uuid);
                User user = online == null ? null : plugin.userManager().getUser(online);
                if (online != null && user == null && ++attempts < FIRST_JOIN_ATTEMPTS) {
                    return;
                }
                cancel();
                firstJoinTasks.remove(uuid);
                if (online != null && user != null && user.isNewPlayer()) {
                    giveFirstJoinKits(online, user);
                }
            }
        };
        firstJoinTasks.put(uuid, runnable.runTaskTimer(plugin, current.firstJoinDelayTicks(), 20L));
    }

    private void giveFirstJoinKits(Player player, User user) {
        for (Kit kit : config.kits().values()) {
            if (!kit.firstJoin()) {
                continue;
            }
            boolean dropped = plan(player, kit).apply(player);
            // Starts the cooldown, so a one-time starter kit cannot be claimed a second time.
            recordUse(user, kit.id());
            plugin.messages().send(player, "kit.first-join", Placeholder.component("kit", kit.displayComponent()));
            if (dropped) {
                plugin.messages().send(player, "kit.dropped");
            }
        }
    }

    private void cancelFirstJoin(UUID uuid) {
        BukkitTask task = firstJoinTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void onQuit(Player player) {
        cancelFirstJoin(player.getUniqueId());
    }

    public void shutdown() {
        for (BukkitTask task : firstJoinTasks.values()) {
            task.cancel();
        }
        firstJoinTasks.clear();
    }
}
