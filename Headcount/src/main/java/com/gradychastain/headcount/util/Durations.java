package com.gradychastain.headcount.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses human durations such as {@code 30s}, {@code 10m}, {@code 1h30m}, {@code 2d}, {@code 1w} or a plain number of seconds. */
public final class Durations {
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
    private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ms|s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?|d|days?|w|weeks?|t|ticks?)");

    private Durations() {}

    /** @return milliseconds, or {@code -1} when the text is not a duration */
    public static long parseMillis(String text) {
        if (text == null) return -1;
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        // Only a bare number means seconds. (Double.parseDouble would also accept "2d" and "3f" as Java literals.)
        if (PLAIN_NUMBER.matcher(s).matches()) return Math.round(Double.parseDouble(s) * 1000.0);
        Matcher m = PART.matcher(s.replace(" ", ""));
        long total = 0;
        int end = 0;
        boolean any = false;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            any = true;
            double v = Double.parseDouble(m.group(1));
            String u = m.group(2);
            double mult;
            if (u.equals("ms")) mult = 1;
            else if (u.startsWith("s")) mult = 1000;
            else if (u.startsWith("mi") || u.equals("m")) mult = 60_000;
            else if (u.startsWith("h")) mult = 3_600_000;
            else if (u.startsWith("d")) mult = 86_400_000;
            else if (u.startsWith("w")) mult = 604_800_000;
            else mult = 50; // ticks
            total += Math.round(v * mult);
        }
        if (!any || end != s.replace(" ", "").length()) return -1;
        return total;
    }

    public static long parseMillis(Object value, long fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return Math.round(n.doubleValue() * 1000.0);
        long v = parseMillis(value.toString());
        return v < 0 ? fallback : v;
    }

    public static long parseTicks(Object value, long fallbackTicks) {
        long ms = parseMillis(value, -1);
        return ms < 0 ? fallbackTicks : Math.max(0, ms / 50);
    }
}
