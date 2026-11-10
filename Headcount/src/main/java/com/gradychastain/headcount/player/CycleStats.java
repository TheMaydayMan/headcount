package com.gradychastain.headcount.player;

/** A player's counters for one cycle (the cycle_players row). */
public final class CycleStats {
    public final int cycle;
    public long firstJoinMs;
    public boolean active;
    public long playtimeMs;
    public int kills;
    public int deaths;
    public int trades;
    public int headsIn;
    public int headsOut;
    public long pointsSpent;
    public int purchases;

    public CycleStats(int cycle) {
        this.cycle = cycle;
    }
}
