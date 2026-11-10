package com.gradychastain.headcount.scoring;

import java.util.UUID;

/** Everything about one player that feeds the score formula. */
public record ScoreInputs(
        UUID uuid,
        String name,
        boolean active,
        int unique,
        int total,
        int own,
        int kills,
        int deaths,
        int trades,
        int headsIn,
        int headsOut,
        int purchases,
        long pointsSpent,
        long playtimeMs,
        int streak,
        Integer lastScore,
        Integer lastUnique,
        long points,
        long pointsNext,
        int bonus,
        int vaultSize,
        int titles) {
}
