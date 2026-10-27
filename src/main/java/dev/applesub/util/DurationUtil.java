package dev.applesub.util;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {

    /** Marker for a subscription without an end date. */
    public static final long FOREVER = -1L;

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private static final Pattern PART = Pattern.compile("(\\d+)([a-zа-яё]+)");
    private static final Set<String> FOREVER_WORDS = Set.of(
            "forever", "perm", "permanent", "inf", "infinite", "навсегда", "-1");
    private static final Map<String, Long> UNITS = Map.ofEntries(
            Map.entry("s", SECOND), Map.entry("sec", SECOND), Map.entry("с", SECOND), Map.entry("сек", SECOND),
            Map.entry("m", MINUTE), Map.entry("min", MINUTE), Map.entry("м", MINUTE), Map.entry("мин", MINUTE),
            Map.entry("h", HOUR), Map.entry("ч", HOUR),
            Map.entry("d", DAY), Map.entry("д", DAY),
            Map.entry("w", 7 * DAY), Map.entry("н", 7 * DAY),
            Map.entry("mo", 30 * DAY), Map.entry("мес", 30 * DAY),
            Map.entry("y", 365 * DAY), Map.entry("г", 365 * DAY));

    private DurationUtil() {
    }

    /**
     * Parses "30d", "1d12h", "forever" etc. A bare number means days.
     *
     * @return milliseconds or {@link #FOREVER}; empty if the input is invalid
     */
    public static OptionalLong parse(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (FOREVER_WORDS.contains(text)) {
            return OptionalLong.of(FOREVER);
        }
        if (text.matches("\\d+")) {
            text += "d";
        }
        Matcher matcher = PART.matcher(text);
        long total = 0;
        int position = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() != position) {
                    return OptionalLong.empty();
                }
                Long unit = UNITS.get(matcher.group(2));
                if (unit == null) {
                    return OptionalLong.empty();
                }
                total = Math.addExact(total, Math.multiplyExact(Long.parseLong(matcher.group(1)), unit));
                position = matcher.end();
            }
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
        if (position != text.length() || total <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(total);
    }

    /** New expiry after adding {@code duration} to a subscription that currently ends at {@code current}. */
    public static long extend(long current, long duration, long now) {
        if (duration == FOREVER || current == FOREVER) {
            return FOREVER;
        }
        long base = Math.max(current, now);
        long result = base + duration;
        return result < base ? Long.MAX_VALUE : result;
    }

    public static boolean isActive(long expiresAt, long now) {
        return expiresAt == FOREVER || expiresAt > now;
    }
}
