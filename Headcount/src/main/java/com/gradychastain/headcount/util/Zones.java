package com.gradychastain.headcount.util;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a time zone from config: a region ID (America/Denver, any case), "system", an offset (GMT-7, UTC+5:30, -07:00)
 * or a common abbreviation (MST, EST, CET, ...). Abbreviations are fixed offsets: MST is always UTC-7, MDT always
 * UTC-6. A region ID follows daylight saving.
 */
public final class Zones {
    private static final Pattern OFFSET = Pattern.compile("(?i)(?:utc|gmt|ut)?\\s*([+-])\\s*(\\d{1,2})(?::?(\\d{2}))?");
    private static final Map<String, String> ABBREVIATIONS = new HashMap<>();

    static {
        String[][] a = {
                {"UTC", "+00:00"}, {"GMT", "+00:00"}, {"Z", "+00:00"}, {"WET", "+00:00"}, {"WEST", "+01:00"}, {"BST", "+01:00"},
                {"CET", "+01:00"}, {"CEST", "+02:00"}, {"EET", "+02:00"}, {"EEST", "+03:00"}, {"MSK", "+03:00"}, {"IST", "+05:30"},
                {"SGT", "+08:00"}, {"AWST", "+08:00"}, {"JST", "+09:00"}, {"KST", "+09:00"}, {"ACST", "+09:30"}, {"AEST", "+10:00"},
                {"AEDT", "+11:00"}, {"NZST", "+12:00"}, {"NZDT", "+13:00"}, {"HST", "-10:00"}, {"AKST", "-09:00"}, {"AKDT", "-08:00"},
                {"PST", "-08:00"}, {"PDT", "-07:00"}, {"MST", "-07:00"}, {"MDT", "-06:00"}, {"CST", "-06:00"}, {"CDT", "-05:00"},
                {"EST", "-05:00"}, {"EDT", "-04:00"}, {"AST", "-04:00"}, {"ADT", "-03:00"}, {"NST", "-03:30"}, {"NDT", "-02:30"},
        };
        for (String[] e : a) ABBREVIATIONS.put(e[0], e[1]);
    }

    private Zones() {}

    /** @throws DateTimeException when the text is none of the accepted forms */
    public static ZoneId parse(String text) {
        String z = text == null ? "" : text.trim();
        if (z.equalsIgnoreCase("system")) return ZoneId.systemDefault();
        String abbr = ABBREVIATIONS.get(z.toUpperCase(Locale.ROOT));
        if (abbr != null) return abbr.equals("+00:00") ? ZoneId.of("UTC") : ZoneId.ofOffset("UTC", ZoneOffset.of(abbr));
        Matcher m = OFFSET.matcher(z);
        if (m.matches()) {
            int h = Integer.parseInt(m.group(2));
            int min = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            int sign = m.group(1).equals("-") ? -1 : 1;
            return ZoneId.ofOffset("UTC", ZoneOffset.ofHoursMinutes(sign * h, sign * min));
        }
        try {
            return ZoneId.of(z);
        } catch (DateTimeException e) {
            for (String id : ZoneId.getAvailableZoneIds()) if (id.equalsIgnoreCase(z)) return ZoneId.of(id);
            throw e;
        }
    }
}
