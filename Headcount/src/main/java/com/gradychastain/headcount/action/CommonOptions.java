package com.gradychastain.headcount.action;

import com.gradychastain.headcount.util.Cfg;

import java.util.Locale;

/**
 * Options every action supports: {@code duration}, {@code time} ({@code real} or {@code online}), {@code stacking}
 * ({@code stack}, {@code refresh}, {@code extend}, {@code ignore}), {@code chance} (0-100), {@code delay},
 * {@code repeat}, {@code conditions}, {@code start-message}, {@code end-message} and {@code key} (stacking identity).
 */
public record CommonOptions(DurationSpec duration, boolean onlineTime, Stacking stacking, double chance, long delayMs,
                            long repeatMs, Conditions conditions, String startMessage, String endMessage, String key) {
    public enum Stacking { STACK, REFRESH, EXTEND, IGNORE }

    public static CommonOptions parse(Cfg c) {
        DurationSpec d = DurationSpec.parse(c.get("duration"));
        String time = c.getString("time", "real").toLowerCase(Locale.ROOT);
        boolean online = time.equals("online") || time.equals("online-only") || time.equals("playtime");
        Stacking stacking;
        try {
            stacking = Stacking.valueOf(c.getString("stacking", "refresh").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("stacking must be stack, refresh, extend or ignore");
        }
        double chance = c.getDouble("chance", 100);
        long delay = Math.max(0, c.getMillis("delay", 0));
        long repeat = Math.max(0, c.getMillis("repeat", 0));
        if (repeat > 0 && repeat < 1000) repeat = 1000;
        Conditions cond = c.isSection("conditions") ? new Conditions(c.getSection("conditions")) : Conditions.NONE;
        return new CommonOptions(d, online, stacking, chance, delay, repeat, cond, c.getString("start-message", null),
                c.getString("end-message", null), c.getString("key", null));
    }
}
