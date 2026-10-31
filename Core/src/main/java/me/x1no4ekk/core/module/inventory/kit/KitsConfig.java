package me.x1no4ekk.core.module.inventory.kit;

import me.x1no4ekk.core.module.inventory.ConfigSupport;
import me.x1no4ekk.core.module.inventory.MenuButton;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable snapshot of kits.yml.
 *
 * @param kits kits ordered by menu slot, keyed by lower-case id
 */
public record KitsConfig(
        FullInventoryMode fullInventory,
        boolean closeOnClaim,
        long createCooldownSeconds,
        int firstJoinDelayTicks,
        GuiSettings gui,
        ContentSettings content,
        Map<String, Kit> kits
) {

    /** Ids double as permission suffixes and as the {@code kit} column (VARCHAR(64)). */
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    /** Ids that would collide with /kit admin subcommands. */
    private static final Set<String> RESERVED_IDS = Set.of("create", "delete", "give");

    public enum FullInventoryMode {
        /** Give the kit and drop what does not fit at the player's feet. */
        DROP,
        /** Refuse the kit until there is enough free space. */
        DENY
    }

    public record GuiSettings(
            String title,
            int rows,
            @Nullable Material filler,
            String iconName,
            String priceFree,
            String pricePaid,
            String cooldownNone,
            String cooldownOneTime,
            List<String> info,
            Map<KitStatus, List<String>> status,
            List<String> actions
    ) {
        public GuiSettings {
            info = List.copyOf(info);
            status = Map.copyOf(status);
            actions = List.copyOf(actions);
        }

        public List<String> status(KitStatus kitStatus) {
            return status.getOrDefault(kitStatus, List.of());
        }

        static GuiSettings parse(ConfigurationSection s) {
            ConfigurationSection statusSection = ConfigSupport.section(s, "status");
            Map<KitStatus, List<String>> status = new EnumMap<>(KitStatus.class);
            for (KitStatus kitStatus : KitStatus.values()) {
                status.put(kitStatus, List.copyOf(statusSection.getStringList(kitStatus.configKey())));
            }
            return new GuiSettings(
                    ConfigSupport.string(s, "title", "Наборы"),
                    ConfigSupport.integer(s, "rows", 3, 1, 6),
                    ConfigSupport.filler(s, "filler"),
                    ConfigSupport.string(s, "icon-name", "<white><kit>"),
                    ConfigSupport.string(s, "price-free", "<green>бесплатно"),
                    ConfigSupport.string(s, "price-paid", "<aqua><amount> АР"),
                    ConfigSupport.string(s, "cooldown-none", "нет"),
                    ConfigSupport.string(s, "cooldown-one-time", "одноразовый"),
                    s.getStringList("info"),
                    status,
                    s.getStringList("actions")
            );
        }
    }

    public record ContentSettings(String title, int rows, @Nullable Material filler,
                                  MenuButton back, MenuButton previousPage, MenuButton nextPage) {

        /** Every row but the last shows kit items. */
        public int itemSlots() {
            return (rows - 1) * 9;
        }

        static ContentSettings parse(ConfigurationSection s) {
            int rows = ConfigSupport.integer(s, "rows", 6, 2, 6);
            int firstButtonSlot = (rows - 1) * 9;
            int lastSlot = rows * 9 - 1;
            MenuButton back = ConfigSupport.button(s, "back", firstButtonSlot, lastSlot);
            MenuButton previous = ConfigSupport.button(s, "previous-page", firstButtonSlot, lastSlot);
            MenuButton next = ConfigSupport.button(s, "next-page", firstButtonSlot, lastSlot);
            if (back.slot() == previous.slot() || back.slot() == next.slot() || previous.slot() == next.slot()) {
                throw new IllegalArgumentException(s.getCurrentPath() + ": buttons must use different slots");
            }
            return new ContentSettings(
                    ConfigSupport.string(s, "title", "<kit>"),
                    rows,
                    ConfigSupport.filler(s, "filler"),
                    back,
                    previous,
                    next
            );
        }
    }

    public KitsConfig {
        kits = Collections.unmodifiableMap(new LinkedHashMap<>(kits));
    }

    public @Nullable Kit kit(String id) {
        return kits.get(id.toLowerCase(Locale.ROOT));
    }

    public static boolean isValidId(String id) {
        return ID_PATTERN.matcher(id).matches() && !RESERVED_IDS.contains(id);
    }

    /**
     * @return the first menu slot not used by a kit, or -1 if the menu is full
     */
    public int freeSlot() {
        Set<Integer> used = new HashSet<>();
        for (Kit kit : kits.values()) {
            used.add(kit.slot());
        }
        for (int slot = 0; slot < gui.rows() * 9; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public static KitsConfig parse(ConfigurationSection root) {
        ConfigurationSection settings = ConfigSupport.section(root, "settings");
        FullInventoryMode mode = ConfigSupport.enumValue(FullInventoryMode.class,
                ConfigSupport.string(settings, "full-inventory", "drop"), ConfigSupport.path(settings, "full-inventory"));
        GuiSettings gui = GuiSettings.parse(ConfigSupport.section(root, "gui"));
        ContentSettings content = ContentSettings.parse(ConfigSupport.section(root, "content-gui"));
        Map<String, Kit> kits = parseKits(root.getConfigurationSection("kits"), gui.rows());

        return new KitsConfig(
                mode,
                ConfigSupport.bool(settings, "close-on-claim", true),
                ConfigSupport.longValue(settings, "create-cooldown-seconds", 86_400, -1, Integer.MAX_VALUE),
                ConfigSupport.integer(settings, "first-join-delay-ticks", 20, 1, 1200),
                gui,
                content,
                kits
        );
    }

    private static Map<String, Kit> parseKits(@Nullable ConfigurationSection section, int rows) {
        if (section == null) {
            return Map.of();
        }
        int menuSize = rows * 9;
        List<KitDraft> drafts = new ArrayList<>();
        Map<Integer, String> slotOwners = new HashMap<>();
        Set<String> ids = new HashSet<>();

        for (String rawId : section.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            String path = ConfigSupport.path(section, rawId);
            if (!isValidId(id)) {
                throw new IllegalArgumentException(path + ": invalid kit id (a-z, 0-9, '_' and '-', up to 32 chars; "
                        + "create/delete/give are reserved)");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException(path + ": duplicate kit id '" + id + "'");
            }
            ConfigurationSection kit = section.getConfigurationSection(rawId);
            if (kit == null) {
                throw new IllegalArgumentException(path + " must be a section");
            }
            int slot = -1;
            if (kit.get("slot") != null) {
                slot = ConfigSupport.integer(kit, "slot", -1, 0, menuSize - 1);
                String other = slotOwners.putIfAbsent(slot, id);
                if (other != null) {
                    throw new IllegalArgumentException(path + ".slot " + slot + " is already used by kit '" + other + "'");
                }
            }
            drafts.add(new KitDraft(id, slot, kit));
        }

        // Kits without an explicit slot take the first free ones, in file order.
        int nextFree = 0;
        List<Kit> result = new ArrayList<>(drafts.size());
        for (KitDraft draft : drafts) {
            int slot = draft.slot();
            if (slot < 0) {
                while (nextFree < menuSize && slotOwners.containsKey(nextFree)) {
                    nextFree++;
                }
                if (nextFree >= menuSize) {
                    throw new IllegalArgumentException("kits: more kits than slots in the menu (gui.rows = " + rows + ")");
                }
                slot = nextFree;
                slotOwners.put(slot, draft.id());
            }
            result.add(parseKit(draft.id(), slot, draft.section()));
        }
        result.sort(Comparator.comparingInt(Kit::slot));

        Map<String, Kit> kits = new LinkedHashMap<>();
        for (Kit kit : result) {
            kits.put(kit.id(), kit);
        }
        return kits;
    }

    private record KitDraft(String id, int slot, ConfigurationSection section) {
    }

    private static Kit parseKit(String id, int slot, ConfigurationSection s) {
        ConfigurationSection icon = s.getConfigurationSection("icon");
        Material iconMaterial = icon == null
                ? Material.CHEST
                : ConfigSupport.material(ConfigSupport.string(icon, "material", "CHEST"), ConfigSupport.path(icon, "material"));

        List<?> rawItems = s.getList("items");
        if (rawItems == null || rawItems.isEmpty()) {
            throw new IllegalArgumentException(ConfigSupport.path(s, "items") + " must contain at least one item");
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < rawItems.size(); i++) {
            items.addAll(KitItems.parse(rawItems.get(i), ConfigSupport.path(s, "items") + "[" + i + "]"));
        }

        return new Kit(
                id,
                ConfigSupport.string(s, "display-name", id),
                slot,
                iconMaterial,
                icon == null ? null : icon.getString("name"),
                icon == null ? List.of() : icon.getStringList("lore"),
                icon != null && ConfigSupport.bool(icon, "glint", false),
                ConfigSupport.longValue(s, "cooldown-seconds", 0, -1, Integer.MAX_VALUE),
                ConfigSupport.decimal(s, "price", 0, 0, 1_000_000_000_000D),
                ConfigSupport.bool(s, "auto-equip", true),
                ConfigSupport.bool(s, "first-join", false),
                items
        );
    }
}
