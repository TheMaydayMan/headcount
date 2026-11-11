package com.gradychastain.headcount.cycle;

import com.gradychastain.headcount.util.Cfg;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.logging.Logger;

/**
 * Cycle timing from config.yml: length in days, the first start date, the time of day cycles roll over, and the
 * time zone. Boundaries are computed on the calendar of that zone, so daylight saving shifts do not move the
 * rollover time.
 */
public final class CycleSchedule {
    private final int lengthDays;
    private final String startDate;
    private final LocalTime endTime;
    private final ZoneId zone;

    public CycleSchedule(Cfg cfg, Logger logger) {
        this.lengthDays = Math.max(1, cfg.getInt("length-days", 14));
        this.startDate = cfg.getString("start-date", "auto").trim();
        LocalTime t;
        try {
            t = LocalTime.parse(cfg.getString("end-time", "00:00").trim());
        } catch (DateTimeParseException e) {
            logger.warning("cycle.end-time '" + cfg.getString("end-time", "") + "' isn't HH:MM. Using 00:00.");
            t = LocalTime.MIDNIGHT;
        }
        this.endTime = t;
        ZoneId z;
        String zs = cfg.getString("time-zone", "UTC").trim();
        try {
            z = com.gradychastain.headcount.util.Zones.parse(zs);
        } catch (RuntimeException e) {
            logger.warning("cycle.time-zone '" + zs + "' isn't a time zone, such as America/Denver, MST, GMT-7 or UTC. Using UTC.");
            z = ZoneId.of("UTC");
        }
        this.zone = z;
    }

    public int lengthDays() {
        return lengthDays;
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalTime endTime() {
        return endTime;
    }

    /** When cycle 1 starts: the configured date at the rollover time, or for {@code auto} the latest rollover at or before {@code now}. */
    public long firstStart(long now, Logger logger) {
        if (!startDate.equalsIgnoreCase("auto")) {
            try {
                return LocalDate.parse(startDate).atTime(endTime).atZone(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                logger.warning("cycle.start-date '" + startDate + "' isn't YYYY-MM-DD or auto. Using auto.");
            }
        }
        ZonedDateTime n = Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime today = n.toLocalDate().atTime(endTime).atZone(zone);
        return (today.isAfter(n) ? today.minusDays(1) : today).toInstant().toEpochMilli();
    }

    /** The rollover {@code days} calendar days after the date of {@code fromMs}, at the configured time. */
    public long boundaryAfter(long fromMs, int days) {
        ZonedDateTime from = Instant.ofEpochMilli(fromMs).atZone(zone);
        ZonedDateTime end = from.toLocalDate().plusDays(days).atTime(endTime).atZone(zone);
        while (!end.isAfter(from)) end = end.plusDays(1);
        return end.toInstant().toEpochMilli();
    }
}
