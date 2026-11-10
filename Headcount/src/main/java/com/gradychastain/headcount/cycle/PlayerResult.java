package com.gradychastain.headcount.cycle;

import java.util.List;
import java.util.UUID;

/** One player's end-of-cycle result. {@code score}, {@code rank} and {@code percentile} are null for inactive players. */
public record PlayerResult(
        UUID uuid,
        String name,
        boolean active,
        int unique,
        int total,
        int own,
        Integer score,
        long pointsEarned,
        long pointsSpent,
        long pointsBefore,
        long pointsAfter,
        long rolledOver,
        int streak,
        Integer rank,
        Double percentile,
        List<String> outcomes,
        long playtimeMs,
        int kills,
        int deaths,
        int trades,
        String tier) {

    /** The same result with the final balance (after outcome actions changed points). */
    public PlayerResult withPointsAfter(long balance) {
        return new PlayerResult(uuid, name, active, unique, total, own, score, pointsEarned, pointsSpent, pointsBefore, balance,
                rolledOver, streak, rank, percentile, outcomes, playtimeMs, kills, deaths, trades, tier);
    }

    public PlayerResult withOutcomes(List<String> applied) {
        return new PlayerResult(uuid, name, active, unique, total, own, score, pointsEarned, pointsSpent, pointsBefore, pointsAfter,
                rolledOver, streak, rank, percentile, List.copyOf(applied), playtimeMs, kills, deaths, trades, tier);
    }
}
