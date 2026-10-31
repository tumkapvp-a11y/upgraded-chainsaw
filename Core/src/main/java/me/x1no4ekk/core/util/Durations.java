package me.x1no4ekk.core.util;

/**
 * Human-readable durations in Russian short units.
 */
public final class Durations {

    private Durations() {
    }

    /**
     * @return e.g. "1д 2ч 5м 10с"; "0с" for non-positive input
     */
    public static String format(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0с";
        }
        long days = totalSeconds / 86_400;
        long hours = totalSeconds % 86_400 / 3_600;
        long minutes = totalSeconds % 3_600 / 60;
        long seconds = totalSeconds % 60;
        StringBuilder builder = new StringBuilder();
        append(builder, days, "д");
        append(builder, hours, "ч");
        append(builder, minutes, "м");
        append(builder, seconds, "с");
        return builder.toString();
    }

    public static String formatMillis(long millis) {
        return format((millis + 999) / 1000);
    }

    private static void append(StringBuilder builder, long value, String unit) {
        if (value <= 0) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value).append(unit);
    }
}
