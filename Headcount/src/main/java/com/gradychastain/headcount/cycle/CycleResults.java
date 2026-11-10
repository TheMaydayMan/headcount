package com.gradychastain.headcount.cycle;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything decided at a cycle's end. {@code simulated} results were never applied. */
public record CycleResults(
        Cycle cycle,
        Map<UUID, PlayerResult> players,
        List<PlayerResult> ranked,
        int active,
        int totalPlayers,
        double averageScore,
        int rewarded,
        int punished,
        boolean simulated) {

    public List<PlayerResult> top(int n) {
        return ranked.subList(0, Math.min(n, ranked.size()));
    }
}
