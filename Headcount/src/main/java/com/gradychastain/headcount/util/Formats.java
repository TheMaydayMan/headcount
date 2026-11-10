package com.gradychastain.headcount.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Number, time and date formatting shared across messages and placeholders. */
public final class Formats {
    private static volatile String[] units = {"d", "h", "m", "s"};
    private static volatile int maxUnits = 2;
    private static volatile DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
    private static volatile ZoneId zone = ZoneId.of("UTC");

    private Formats() {}

    public static void configure(String days, String hours, String minutes, String seconds, int max, String datePattern, ZoneId z) {
        units = new String[]{days, hours, minutes, seconds};
        maxUnits = Math.max(1, max);
        zone = z;
        try {
            dateFormat = DateTimeFormatter.ofPattern(datePattern, Locale.ROOT);
        } catch (IllegalArgumentException e) {
            dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
        }
    }

    public static String number(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Formats a duration like {@code 2d 3h}, keeping the configured number of largest units. */
    public static String duration(long millis) {
        if (millis < 0) millis = 0;
        long secs = (millis + 999) / 1000;
        long[] parts = {secs / 86400, (secs % 86400) / 3600, (secs % 3600) / 60, secs % 60};
        int first = -1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != 0) { first = i; break; }
        }
        if (first < 0) return "0" + units[3];
        StringBuilder sb = new StringBuilder();
        // Show up to maxUnits consecutive units starting at the largest non-zero one, skipping zeros ("1d 0h" -> "1d").
        for (int i = first; i < Math.min(parts.length, first + maxUnits); i++) {
            if (parts[i] == 0) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(parts[i]).append(units[i]);
        }
        return sb.toString();
    }

    /** Formats a duration as {@code HH:MM:SS} or {@code MM:SS}. */
    public static String clock(long millis) {
        long secs = Math.max(0, (millis + 999) / 1000);
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    public static String date(long epochMillis) {
        return dateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    public static ZoneId zone() {
        return zone;
    }

    public static String shortId(String uuid) {
        return uuid == null ? "" : uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }
}
