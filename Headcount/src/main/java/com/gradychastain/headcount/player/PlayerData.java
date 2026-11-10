package com.gradychastain.headcount.player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cached Headcount data for an online player. The database is updated as each value changes; this object is the
 * in-memory copy used for fast reads. Fields are mutated on the main thread only.
 */
public final class PlayerData {
    public final UUID uuid;
    public String name;
    public boolean exists;          // a players row exists
    public long firstJoin;
    public long lastJoin;
    public long points;
    public long pointsNext;
    public int streak;              // >0 positive streak, <0 negative streak
    public Integer lastScore;
    public Integer lastUnique;
    public boolean sidebar;
    public String equippedTitle;
    public int setupCycle;
    public int vaultSize;
    public String nextHeadsMod;
    public String nextSizeMod;
    public Double rolloverOverride;
    public int scoreBonus;
    public boolean bedrock;
    public final Set<String> titles = new HashSet<>();
    public final Set<String> flags = new HashSet<>();
    public CycleStats stats;        // current cycle; null before joining it
    public final Map<String, Integer> purchasesThisCycle = new HashMap<>();
    public final Map<String, Long> lastPurchase = new HashMap<>();
    public final Set<String> everPurchased = new HashSet<>();
    /** Session bookkeeping. */
    public long sessionMark;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
