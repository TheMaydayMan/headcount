package com.gradychastain.headcount.action;

import com.gradychastain.headcount.util.Durations;

import java.util.Locale;

/**
 * How long an action lasts: {@code instant} (default), a time such as {@code 10m}, {@code cycle} (until the cycle
 * ends), {@code cycles:N}, {@code death} (until the player dies), {@code permanent}, or {@code manual} (until an admin
 * removes it).
 */
public record DurationSpec(Kind kind, long millis, int cycles) {
    public enum Kind { INSTANT, TIME, CYCLE, CYCLES, DEATH, PERMANENT, MANUAL }

    public static final DurationSpec INSTANT = new DurationSpec(Kind.INSTANT, 0, 0);

    public static DurationSpec parse(Object raw) {
        if (raw == null) return INSTANT;
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "", "instant", "none", "0" -> { return INSTANT; }
            case "cycle", "until-cycle-end", "until-cycle-ends" -> { return new DurationSpec(Kind.CYCLE, 0, 1); }
            case "death", "until-death" -> { return new DurationSpec(Kind.DEATH, 0, 0); }
            case "permanent", "forever" -> { return new DurationSpec(Kind.PERMANENT, 0, 0); }
            case "manual", "until-removed", "admin" -> { return new DurationSpec(Kind.MANUAL, 0, 0); }
            default -> { }
        }
        if (s.startsWith("cycles:") || s.startsWith("cycles ")) {
            try {
                int n = Integer.parseInt(s.substring(7).trim());
                return new DurationSpec(Kind.CYCLES, 0, Math.max(1, n));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad duration '" + raw + "' (use cycles:N)");
            }
        }
        long ms = Durations.parseMillis(s);
        if (ms < 0) throw new IllegalArgumentException("bad duration '" + raw + "' (use instant, 10m, 2h, cycle, cycles:3, death, permanent or manual)");
        return ms == 0 ? INSTANT : new DurationSpec(Kind.TIME, ms, 0);
    }

    public boolean instant() {
        return kind == Kind.INSTANT;
    }

    public String describe() {
        return switch (kind) {
            case INSTANT -> "instant";
            case TIME -> com.gradychastain.headcount.util.Formats.duration(millis);
            case CYCLE -> "until the cycle ends";
            case CYCLES -> cycles + " cycles";
            case DEATH -> "until death";
            case PERMANENT -> "permanent";
            case MANUAL -> "until removed";
        };
    }
}
