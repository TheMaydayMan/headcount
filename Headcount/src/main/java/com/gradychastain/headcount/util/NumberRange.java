package com.gradychastain.headcount.util;

import java.util.Locale;

/**
 * An inclusive numeric range parsed from config. Accepted forms:
 * {@code 5}, {@code 1..3}, {@code ..-1}, {@code 10..}, {@code >=2}, {@code >2}, {@code <=3}, {@code <3}, {@code !=0}, {@code any}.
 */
public record NumberRange(double min, double max, boolean minExclusive, boolean maxExclusive, Double notEqual) {
    public static final NumberRange ANY = new NumberRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, false, null);

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");

    /** Strict number parsing: rejects Java literal suffixes such as "5d" that Double.parseDouble accepts. */
    private static double num(String s) {
        if (!NUMBER.matcher(s).matches()) throw new NumberFormatException(s);
        return Double.parseDouble(s);
    }

    public static NumberRange parse(Object raw) {
        if (raw == null) return ANY;
        if (raw instanceof Number n) return new NumberRange(n.doubleValue(), n.doubleValue(), false, false, null);
        String s = raw.toString().trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty() || s.equals("any") || s.equals("*")) return ANY;
        try {
            if (s.startsWith(">=")) return new NumberRange(num(s.substring(2)), Double.POSITIVE_INFINITY, false, false, null);
            if (s.startsWith("<=")) return new NumberRange(Double.NEGATIVE_INFINITY, num(s.substring(2)), false, false, null);
            if (s.startsWith("!=")) return new NumberRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, false, num(s.substring(2)));
            if (s.startsWith(">")) return new NumberRange(num(s.substring(1)), Double.POSITIVE_INFINITY, true, false, null);
            if (s.startsWith("<")) return new NumberRange(Double.NEGATIVE_INFINITY, num(s.substring(1)), false, true, null);
            if (s.startsWith("=")) s = s.substring(1);
            int dots = s.indexOf("..");
            if (dots >= 0) {
                String a = s.substring(0, dots);
                String b = s.substring(dots + 2);
                double min = a.isEmpty() ? Double.NEGATIVE_INFINITY : num(a);
                double max = b.isEmpty() ? Double.POSITIVE_INFINITY : num(b);
                return new NumberRange(Math.min(min, max), Math.max(min, max), false, false, null);
            }
            double v = num(s);
            return new NumberRange(v, v, false, false, null);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid range '" + raw + "' (use 5, 1..3, ..-1, 10.., >=2, <3)");
        }
    }

    public boolean test(double v) {
        if (notEqual != null && v == notEqual) return false;
        if (minExclusive ? v <= min : v < min) return false;
        return maxExclusive ? v < max : v <= max;
    }

    public boolean isAny() {
        return this.equals(ANY);
    }

    /** Distance of the bound nearest zero from zero; used to rank how "extreme" a rule is. */
    public double extremeness() {
        if (max < 0) return Math.abs(max);
        if (min > 0) return min;
        if (max == 0 && min < 0) return 0;
        return 0;
    }

    public String describe() {
        if (isAny()) return "any";
        String lo = Double.isInfinite(min) ? "" : Formats.number(min);
        String hi = Double.isInfinite(max) ? "" : Formats.number(max);
        if (min == max) return lo;
        if (lo.isEmpty()) return (maxExclusive ? "<" : "≤") + hi;
        if (hi.isEmpty()) return (minExclusive ? ">" : "≥") + lo;
        return lo + ".." + hi;
    }
}
