package com.gradychastain.headcount.cycle;

import java.util.UUID;

/**
 * One cycle. Its length and vault settings are fixed when it starts, so config changes apply from the next cycle.
 *
 * @param token identifies this run of the cycle; it is written on every head made in it, so heads from before a
 *              {@code /hca reset cycle} (same ID) or a {@code /hca reset hard} (IDs start over) never count. Null for a
 *              cycle that started before 1.1, whose heads carry no token.
 */
public record Cycle(int id, long startMs, long endMs, int lengthDays, int vaultSize, int startingHeads, String token) {
    public long remainingMs() {
        return Math.max(0, endMs - System.currentTimeMillis());
    }

    public static String newToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
