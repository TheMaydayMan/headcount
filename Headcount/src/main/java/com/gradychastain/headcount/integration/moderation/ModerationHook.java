package com.gradychastain.headcount.integration.moderation;

import java.time.Duration;
import java.util.UUID;

/** A moderation plugin. Durations are null for permanent punishments. */
public interface ModerationHook {
    enum Kind { BAN, MUTE, KICK, WARN, JAIL }

    String name();

    boolean supports(Kind kind);

    boolean apply(Kind kind, UUID uuid, String name, String reason, Duration duration, String jail);

    /** Lifts a ban, mute or jail. Kicks and warnings cannot be revoked and return true. */
    boolean revoke(Kind kind, UUID uuid, String name);

    /** Null when the provider cannot tell. */
    default Boolean isMuted(UUID uuid) {
        return null;
    }

    /** True when Headcount enforces this kind itself (vanilla mutes and jails). */
    default boolean enforcedByHeadcount(Kind kind) {
        return false;
    }

    /** Formats a duration the way most ban plugins accept it, e.g. {@code 1d2h30m} or {@code 45s}. */
    static String compact(Duration d) {
        long s = Math.max(1, d.toSeconds());
        StringBuilder sb = new StringBuilder();
        long days = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (days > 0) sb.append(days).append('d');
        if (h > 0) sb.append(h).append('h');
        if (m > 0) sb.append(m).append('m');
        if (sec > 0 && days == 0) sb.append(sec).append('s');
        return sb.isEmpty() ? "1m" : sb.toString();
    }
}
