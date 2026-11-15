package com.gradychastain.headcount;

import com.gradychastain.headcount.scoring.Expression;
import com.gradychastain.headcount.util.Zones;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The bundled score formula against the table in TEST-NIGHT.md ("Numbers that depend on N"), so a change to either
 * shows up here, and the time-zone forms config.yml documents for cycle.time-zone.
 */
class BalanceAndZonesTest {
    private static String bundledFormula() throws IOException {
        try (InputStream in = BalanceAndZonesTest.class.getResourceAsStream("/scoring.yml")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(?m)^formula: \"(.*)\"$").matcher(text);
            if (!m.find()) throw new IllegalStateException("no formula line in scoring.yml");
            return m.group(1);
        }
    }

    private static int score(Expression f, int unique, int active) {
        return (int) Math.floor(f.evaluate(Map.of("unique", (double) unique, "active", (double) active, "vault_size", 27.0)::get));
    }

    @Test
    void bundledFormulaMatchesTheTestNightTable() throws IOException {
        Expression f = Expression.parse(bundledFormula());
        // N: never trades (1 unique), 1 other head, 2 other heads, full set (N unique)
        int[][] table = {
                {2, 0, 7, -1, 7}, {3, -1, 3, 7, 7}, {4, -1, 2, 5, 8}, {5, -1, 2, 4, 9}, {6, -1, 2, 4, 10},
                {7, -2, 0, 2, 10}, {8, -2, 0, 2, 11}, {9, -2, 0, 2, 12}, {10, -2, 0, 2, 13},
        };
        for (int[] row : table) {
            int n = row[0];
            assertEquals(row[1], score(f, 1, n), "never trades, N=" + n);
            assertEquals(row[2], score(f, 2, n), "1 other head, N=" + n);
            if (n >= 3) assertEquals(row[3], score(f, 3, n), "2 other heads, N=" + n);
            assertEquals(row[4], score(f, n, n), "full set, N=" + n);
        }
    }

    @Test
    void timeZoneForms() {
        assertEquals("UTC-07:00", Zones.parse("MST").getId());
        assertEquals("UTC-07:00", Zones.parse("mst").getId());
        assertEquals("UTC-07:00", Zones.parse("gmt-7").getId());
        assertEquals("UTC-07:00", Zones.parse("GMT-7").getId());
        assertEquals("UTC-07:00", Zones.parse("-07:00").getId());
        assertEquals("UTC+05:30", Zones.parse("UTC+5:30").getId());
        assertEquals("UTC+05:30", Zones.parse("+0530").getId());
        assertEquals("UTC+05:30", Zones.parse("IST").getId());
        assertEquals("America/Denver", Zones.parse("america/denver").getId());
        assertEquals("America/Denver", Zones.parse(" America/Denver ").getId());
        assertEquals("Europe/London", Zones.parse("EUROPE/LONDON").getId());
        assertEquals("UTC", Zones.parse("UTC").getId());
        assertEquals("UTC", Zones.parse("GMT").getId());
        assertEquals(ZoneId.systemDefault(), Zones.parse("system"));
        assertEquals(ZoneId.systemDefault(), Zones.parse("SYSTEM"));
        assertThrows(DateTimeException.class, () -> Zones.parse("nonsense"));
        assertThrows(DateTimeException.class, () -> Zones.parse("UTC+25"));
        assertThrows(DateTimeException.class, () -> Zones.parse(""));
    }
}
