package me.x1no4ekk.core.module.warp;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable, validated view of warps.yml. Invalid values throw {@link IllegalArgumentException}.
 */
record WarpConfig(
        Pattern namePattern,
        int defaultLimit,
        Map<String, Integer> limits,
        double price,
        Material defaultIcon,
        Set<String> disabledWorlds,
        long gradeCooldownMillis,
        DateTimeFormatter dateFormat,
        String serverOwner,
        String unknownOwner,
        String ratingFormat,
        String noRating,
        MainMenu mainMenu,
        ListMenu systemMenu,
        ListMenu playersMenu,
        ListMenu myMenu,
        GradeMenu gradeMenu,
        Tops tops
) {

    /** core_warps.name is VARCHAR(32), regardless of the configured pattern. */
    static final int MAX_NAME_LENGTH = 32;

    record Button(int slot, Material material, String name, List<String> lore) {
        static Button load(ConfigurationSection s, int rows) {
            return new Button(
                    ConfigReader.slot(s, "slot", rows),
                    ConfigReader.material(s.getString("material"), ConfigReader.path(s, "material")),
                    s.getString("name", ""),
                    List.copyOf(s.getStringList("lore"))
            );
        }
    }

    record Filler(boolean enabled, Material material) {
        static Filler load(ConfigurationSection s) {
            return new Filler(s.getBoolean("enabled", true),
                    ConfigReader.material(s.getString("material", "GRAY_STAINED_GLASS_PANE"), ConfigReader.path(s, "material")));
        }
    }

    record ItemTemplate(String name, List<String> lore) {
        static ItemTemplate load(ConfigurationSection s) {
            return new ItemTemplate(s.getString("name", "<gold><warp>"), List.copyOf(s.getStringList("lore")));
        }
    }

    record MainMenu(String title, int rows, Filler filler, Button system, Button players, Button my, Button close) {
        static MainMenu load(ConfigurationSection s) {
            int rows = ConfigReader.rows(s);
            return new MainMenu(
                    s.getString("title", "Варпы"),
                    rows,
                    Filler.load(ConfigReader.section(s, "filler")),
                    Button.load(ConfigReader.section(s, "system-button"), rows),
                    Button.load(ConfigReader.section(s, "players-button"), rows),
                    Button.load(ConfigReader.section(s, "my-button"), rows),
                    Button.load(ConfigReader.section(s, "close"), rows)
            );
        }
    }

    /**
     * Paginated warp list (server, players, own).
     */
    record ListMenu(String title, int rows, List<Integer> contentSlots, Filler filler, ItemTemplate warpItem,
                    boolean ownerHeads, Button empty, Button previousPage, Button nextPage, Button back) {
        static ListMenu load(ConfigurationSection s) {
            int rows = ConfigReader.rows(s);
            return new ListMenu(
                    s.getString("title", "Варпы"),
                    rows,
                    ConfigReader.slots(s, "content-slots", rows),
                    Filler.load(ConfigReader.section(s, "filler")),
                    ItemTemplate.load(ConfigReader.section(s, "warp-item")),
                    s.getBoolean("owner-heads", false),
                    Button.load(ConfigReader.section(s, "empty-item"), rows),
                    Button.load(ConfigReader.section(s, "previous-page"), rows),
                    Button.load(ConfigReader.section(s, "next-page"), rows),
                    Button.load(ConfigReader.section(s, "back"), rows)
            );
        }
    }

    record GradeButton(int slot, String texture) {
    }

    /**
     * @param grades grade (1..5) to its head button, highest first
     */
    record GradeMenu(String title, int rows, Filler filler, int warpSlot, ItemTemplate warpItem,
                     ItemTemplate gradeItem, Map<Integer, GradeButton> grades, Button back) {
        static GradeMenu load(ConfigurationSection s) {
            int rows = ConfigReader.rows(s);
            ConfigurationSection warpItem = ConfigReader.section(s, "warp-item");
            ConfigurationSection gradesSection = ConfigReader.section(s, "grades");
            Map<Integer, GradeButton> grades = new TreeMap<>((a, b) -> Integer.compare(b, a));
            for (String key : gradesSection.getKeys(false)) {
                int grade;
                try {
                    grade = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(ConfigReader.path(gradesSection, key) + ": grade must be a number 1-5");
                }
                if (grade < 1 || grade > 5) {
                    throw new IllegalArgumentException(ConfigReader.path(gradesSection, key) + ": grade must be 1-5");
                }
                ConfigurationSection entry = ConfigReader.section(gradesSection, key);
                String texture = entry.getString("texture", "").trim();
                if (texture.startsWith("head:")) {
                    texture = texture.substring("head:".length());
                }
                if (texture.isEmpty()) {
                    throw new IllegalArgumentException(ConfigReader.path(entry, "texture") + " must not be empty");
                }
                grades.put(grade, new GradeButton(ConfigReader.slot(entry, "slot", rows), texture));
            }
            if (grades.isEmpty()) {
                throw new IllegalArgumentException(ConfigReader.path(s, "grades") + " must contain at least one grade");
            }
            return new GradeMenu(
                    s.getString("title", "Оценка варпа <warp>"),
                    rows,
                    Filler.load(ConfigReader.section(s, "filler")),
                    ConfigReader.slot(warpItem, "slot", rows),
                    ItemTemplate.load(warpItem),
                    ItemTemplate.load(ConfigReader.section(s, "grade-item")),
                    Collections.unmodifiableMap(new LinkedHashMap<>(grades)),
                    Button.load(ConfigReader.section(s, "back"), rows)
            );
        }
    }

    /**
     * @param balanceQuery SELECT returning (name, balance) with one {@code ?} for the limit
     */
    record Tops(int refreshMinutes, int size, String balanceQuery) {
        static Tops load(ConfigurationSection s) {
            String query = s.getString("balance-query", "").trim();
            if (!query.toLowerCase(Locale.ROOT).startsWith("select")) {
                throw new IllegalArgumentException(ConfigReader.path(s, "balance-query") + " must be a SELECT statement");
            }
            if (query.chars().filter(c -> c == '?').count() != 1) {
                throw new IllegalArgumentException(ConfigReader.path(s, "balance-query") + " must contain exactly one '?' (LIMIT)");
            }
            return new Tops(Math.clamp(s.getInt("refresh-minutes", 5), 1, 1440), Math.clamp(s.getInt("size", 10), 1, 100), query);
        }
    }

    static WarpConfig load(YamlConfiguration config) {
        ConfigurationSection warps = ConfigReader.section(config, "warps");

        Pattern pattern;
        String rawPattern = warps.getString("name-pattern", "^[a-zA-Z0-9_-]{1,16}$");
        try {
            pattern = Pattern.compile(rawPattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("warps.name-pattern is not a valid regular expression: " + e.getMessage());
        }

        Map<String, Integer> limits = new LinkedHashMap<>();
        ConfigurationSection limitSection = warps.getConfigurationSection("limits");
        if (limitSection != null) {
            for (String group : limitSection.getKeys(false)) {
                limits.put(group.toLowerCase(Locale.ROOT), Math.max(0, limitSection.getInt(group)));
            }
        }

        double price = warps.getDouble("price", 0);
        if (Double.isNaN(price) || Double.isInfinite(price) || price < 0) {
            throw new IllegalArgumentException("warps.price must be a non-negative number");
        }

        Set<String> disabledWorlds = new HashSet<>();
        for (String world : warps.getStringList("disabled-worlds")) {
            disabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }

        DateTimeFormatter dateFormat;
        try {
            dateFormat = DateTimeFormatter.ofPattern(warps.getString("date-format", "dd.MM.yyyy HH:mm"))
                    .withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new IllegalArgumentException("warps.date-format is invalid: " + e.getMessage());
        }

        ConfigurationSection gui = ConfigReader.section(config, "gui");
        return new WarpConfig(
                pattern,
                Math.max(0, warps.getInt("default-limit", 1)),
                Map.copyOf(limits),
                price,
                ConfigReader.material(warps.getString("default-icon", "ENDER_PEARL"), "warps.default-icon"),
                Set.copyOf(disabledWorlds),
                TimeUnit.SECONDS.toMillis(Math.max(0, warps.getLong("grade-cooldown-seconds", 300))),
                dateFormat,
                warps.getString("server-owner", "Сервер"),
                warps.getString("unknown-owner", "Неизвестно"),
                warps.getString("rating-format", "<gold><average> ★</gold>"),
                warps.getString("no-rating", "<yellow>Нет оценок</yellow>"),
                MainMenu.load(ConfigReader.section(gui, "main")),
                ListMenu.load(ConfigReader.section(gui, "system")),
                ListMenu.load(ConfigReader.section(gui, "players")),
                ListMenu.load(ConfigReader.section(gui, "my")),
                GradeMenu.load(ConfigReader.section(gui, "grade")),
                Tops.load(ConfigReader.section(config, "tops"))
        );
    }
}
