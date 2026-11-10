package com.gradychastain.headcount.cycle;

/**
 * One cycle. Its length and vault settings are fixed when it starts, so config changes apply from the next cycle.
 */
public record Cycle(int id, long startMs, long endMs, int lengthDays, int vaultSize, int startingHeads) {
    public long remainingMs() {
        return Math.max(0, endMs - System.currentTimeMillis());
    }
}
