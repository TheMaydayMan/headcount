package com.gradychastain.headcount;

import com.gradychastain.headcount.action.DurationSpec;
import com.gradychastain.headcount.cycle.CycleSchedule;
import com.gradychastain.headcount.scoring.Population;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Durations;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.util.NumberRange;
import com.gradychastain.headcount.vault.VaultManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicTest {
    private static final Logger LOG = Logger.getLogger("test");

    @Test
    void ranges() {
        NumberRange r = NumberRange.parse("..-1");
        assertTrue(r.test(-1));
        assertTrue(r.test(-50));
        assertFalse(r.test(0));
        assertTrue(NumberRange.parse("-4..-1").test(-4));
        assertFalse(NumberRange.parse("-4..-1").test(-5));
        assertTrue(NumberRange.parse("10..").test(10));
        assertTrue(NumberRange.parse(">=2").test(2));
        assertFalse(NumberRange.parse(">2").test(2));
        assertTrue(NumberRange.parse("<3").test(2.9));
        assertFalse(NumberRange.parse("<3").test(3));
        assertFalse(NumberRange.parse("!=0").test(0));
        assertTrue(NumberRange.parse("5").test(5));
        assertTrue(NumberRange.parse("any").test(-1e9));
        assertTrue(NumberRange.parse(3).test(3));
        assertThrows(IllegalArgumentException.class, () -> NumberRange.parse("abc"));
        // most-extreme ordering: ..-10 beats ..-5 beats ..-1
        assertTrue(NumberRange.parse("..-10").extremeness() > NumberRange.parse("..-5").extremeness());
        assertTrue(NumberRange.parse("..-5").extremeness() > NumberRange.parse("..-1").extremeness());
        assertEquals(10, NumberRange.parse("10..").extremeness());
    }

    @Test
    void durations() {
        assertEquals(30_000, Durations.parseMillis("30s"));
        assertEquals(600_000, Durations.parseMillis("10m"));
        assertEquals(5_400_000, Durations.parseMillis("1h30m"));
        assertEquals(172_800_000, Durations.parseMillis("2d"));
        assertEquals(604_800_000, Durations.parseMillis("1w"));
        assertEquals(150, Durations.parseMillis("150ms"));
        assertEquals(1000, Durations.parseMillis("20t"));
        assertEquals(45_000, Durations.parseMillis("45"));
        assertEquals(-1, Durations.parseMillis("soon"));
        assertEquals(-1, Durations.parseMillis("10x"));
        assertEquals(-1, Durations.parseMillis("2f"));
        assertEquals(3 * 86_400_000L, Durations.parseMillis("3d"));
        assertThrows(IllegalArgumentException.class, () -> NumberRange.parse("5d"));
        assertEquals(5000, Durations.parseMillis((Object) 5, -1));
    }

    @Test
    void durationSpec() {
        assertEquals(DurationSpec.Kind.INSTANT, DurationSpec.parse(null).kind());
        assertEquals(DurationSpec.Kind.TIME, DurationSpec.parse("10m").kind());
        assertEquals(600_000, DurationSpec.parse("10m").millis());
        assertEquals(DurationSpec.Kind.CYCLE, DurationSpec.parse("cycle").kind());
        assertEquals(3, DurationSpec.parse("cycles:3").cycles());
        assertEquals(DurationSpec.Kind.DEATH, DurationSpec.parse("death").kind());
        assertEquals(DurationSpec.Kind.PERMANENT, DurationSpec.parse("permanent").kind());
        assertEquals(DurationSpec.Kind.MANUAL, DurationSpec.parse("manual").kind());
        assertThrows(IllegalArgumentException.class, () -> DurationSpec.parse("whenever"));
    }

    @Test
    void ranksAndPercentiles() {
        Population p = new Population(List.of(10, 10, 8, 3), 20, false);
        assertEquals(1, p.rank(10));
        assertEquals(3, p.rank(8));
        assertEquals(4, p.rank(3));
        assertEquals(4, p.active());
        assertEquals(20, p.totalPlayers());
        assertEquals(100, p.percentile(10), 1e-9);
        assertEquals(0, p.percentile(3), 1e-9);
        assertEquals(100.0 / 3, p.percentile(8), 1e-9);
        assertEquals(9, p.median(), 1e-9);
        assertEquals(7.75, p.average(), 1e-9);
        Population dense = new Population(List.of(10, 10, 8, 3), 4, true);
        assertEquals(1, dense.rank(10));
        assertEquals(2, dense.rank(8));
        assertEquals(3, dense.rank(3));
        assertEquals(100, new Population(List.of(4), 1, false).percentile(4), 1e-9);
    }

    @Test
    void cycleBoundaries() {
        Cfg cfg = Cfg.of(Map.of("length-days", 30, "start-date", "2026-11-01", "end-time", "00:00", "time-zone", "America/Denver"));
        CycleSchedule s = new CycleSchedule(cfg, LOG);
        ZoneId z = ZoneId.of("America/Denver");
        long first = s.firstStart(System.currentTimeMillis(), LOG);
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(first), z).toLocalDateTime());
        // DST ends on 2026-11-01 in Denver: the next rollover is still exactly 00:00 local, 30 calendar days later.
        long end = s.boundaryAfter(first, 30);
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), z).toLocalDateTime());
        // "auto" starts at the most recent rollover at or before now
        CycleSchedule auto = new CycleSchedule(Cfg.of(Map.of("start-date", "auto", "end-time", "06:00", "time-zone", "UTC")), LOG);
        long at = ZonedDateTime.of(2026, 11, 10, 5, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli();
        assertEquals(ZonedDateTime.of(2026, 11, 9, 6, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(), auto.firstStart(at, LOG));
        // a bad zone falls back to UTC instead of failing
        assertEquals("UTC", new CycleSchedule(Cfg.of(Map.of("time-zone", "Mars/Olympus")), LOG).zone().getId());
    }

    @Test
    void nextCycleModifiers() {
        assertEquals("+5", VaultManager.combineMods("+2", "+3"));
        assertEquals("=12", VaultManager.combineMods("=10", "+2"));
        assertEquals("=10", VaultManager.combineMods("+2", "=10"));
        assertEquals("-2", VaultManager.combineMods("-3", "+1"));
        assertEquals("+4", VaultManager.combineMods(null, "+4"));
    }

    @Test
    void cfgReading() {
        Cfg c = Cfg.of(Map.of("a", Map.of("b", 3, "list", List.of("0-3", "9")), "flag", "yes", "time", "2m"));
        assertEquals(3, c.getInt("a.b", 0));
        assertEquals(List.of(0, 1, 2, 3, 9), c.getSection("a").getIntList("list"));
        assertTrue(c.getBool("flag", false));
        assertEquals(120_000, c.getMillis("time", 0));
        Cfg merged = Cfg.of(Map.of("x", Map.of("y", 1, "z", 2))).merge(Cfg.of(Map.of("x", Map.of("z", 5))));
        assertEquals(1, merged.getInt("x.y", 0));
        assertEquals(5, merged.getInt("x.z", 0));
    }

    @Test
    void durationFormatting() {
        Formats.configure("d", "h", "m", "s", 2, "yyyy-MM-dd HH:mm z", ZoneId.of("UTC"));
        assertEquals("1d", Formats.duration(86_400_000L));
        assertEquals("2d 3h", Formats.duration(2 * 86_400_000L + 3 * 3_600_000L + 59_000));
        assertEquals("5m", Formats.duration(300_000));
        assertEquals("0s", Formats.duration(0));
        assertEquals("1:01:01", Formats.clock(3_661_000));
        assertEquals("7", Formats.number(7.0));
        assertEquals("1.5", Formats.number(1.5));
    }
}
