package com.gradychastain.headcount.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The active players of a cycle as seen by the formula: their unique head counts, plus totals.
 * Ranks are by unique heads, highest first; ties share a rank.
 */
public final class Population {
    private final List<Integer> sortedDesc;
    private final int activeCount;
    private final int totalPlayers;
    private final boolean dense;

    public Population(List<Integer> uniques, int totalPlayers, boolean dense) {
        List<Integer> s = new ArrayList<>(uniques);
        s.sort(Collections.reverseOrder());
        this.sortedDesc = s;
        this.activeCount = s.size();
        this.totalPlayers = totalPlayers;
        this.dense = dense;
    }

    public int active() {
        return activeCount;
    }

    public int totalPlayers() {
        return totalPlayers;
    }

    /** Rank of a unique count among active players (1 = most). */
    public int rank(int unique) {
        // dense: 10,10,8 -> 1,1,2   competition: 10,10,8 -> 1,1,3
        if (dense) return 1 + (int) sortedDesc.stream().filter(u -> u > unique).distinct().count();
        int above = 0;
        for (int u : sortedDesc) if (u > unique) above++;
        return above + 1;
    }

    /** 100 = top, 0 = bottom, from the competition rank (players tied for first are all 100); 100 when alone. */
    public double percentile(int unique) {
        if (activeCount <= 1) return 100;
        int above = 0;
        for (int u : sortedDesc) if (u > unique) above++;
        int rank = above + 1;
        return Math.max(0, Math.min(100, (activeCount - rank) * 100.0 / (activeCount - 1)));
    }

    public double average() {
        return sortedDesc.isEmpty() ? 0 : sortedDesc.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    public int max() {
        return sortedDesc.isEmpty() ? 0 : sortedDesc.getFirst();
    }

    public int min() {
        return sortedDesc.isEmpty() ? 0 : sortedDesc.getLast();
    }

    public int sum() {
        return sortedDesc.stream().mapToInt(Integer::intValue).sum();
    }

    public double median() {
        if (sortedDesc.isEmpty()) return 0;
        int n = sortedDesc.size();
        return n % 2 == 1 ? sortedDesc.get(n / 2) : (sortedDesc.get(n / 2 - 1) + sortedDesc.get(n / 2)) / 2.0;
    }
}
