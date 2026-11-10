package com.gradychastain.headcount.scoring;

import com.gradychastain.headcount.HeadcountPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A periodically refreshed snapshot of the current cycle: unique heads per vault, who is active, and the top list.
 * Used for live previews, /hc top and placeholders. Refreshing runs on the database thread.
 */
public final class LiveStats {
    public record Entry(UUID uuid, String name, int unique, int total) {}

    private final HeadcountPlugin plugin;
    private volatile Map<UUID, Integer> activeUniques = Map.of();
    private volatile List<Entry> top = List.of();
    private volatile int totalPlayers;
    private volatile int activeCount;
    private volatile long refreshedAt;
    private volatile boolean refreshing;

    public LiveStats(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        int cycle = plugin.cycles().currentId();
        if (refreshing) return;
        refreshing = true;
        plugin.db().query(c -> {
            Map<UUID, int[]> tallies = new HashMap<>();
            if (cycle > 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT uuid, COUNT(DISTINCT head_owner), COUNT(*) FROM vault_items WHERE cycle=? GROUP BY uuid")) {
                    ps.setInt(1, cycle);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) tallies.put(UUID.fromString(rs.getString(1)), new int[]{rs.getInt(2), rs.getInt(3)});
                    }
                }
            }
            Map<UUID, Integer> active = new HashMap<>();
            Map<UUID, String> names = new HashMap<>();
            if (cycle > 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT cp.uuid, p.name FROM cycle_players cp LEFT JOIN players p ON p.uuid = cp.uuid WHERE cp.cycle=? AND cp.active=1")) {
                    ps.setInt(1, cycle);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            UUID id = UUID.fromString(rs.getString(1));
                            int[] t = tallies.get(id);
                            active.put(id, t == null ? 0 : t[0]);
                            names.put(id, rs.getString(2) == null ? id.toString().substring(0, 8) : rs.getString(2));
                        }
                    }
                }
            }
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM players"); ResultSet rs = ps.executeQuery()) {
                total = rs.next() ? rs.getInt(1) : 0;
            }
            List<Entry> list = new ArrayList<>();
            for (Map.Entry<UUID, Integer> e : active.entrySet()) {
                int[] t = tallies.get(e.getKey());
                list.add(new Entry(e.getKey(), names.get(e.getKey()), e.getValue(), t == null ? 0 : t[1]));
            }
            list.sort((a, b) -> b.unique() != a.unique() ? Integer.compare(b.unique(), a.unique()) : a.name().compareToIgnoreCase(b.name()));
            activeUniques = active;
            top = list;
            totalPlayers = total;
            activeCount = active.size();
            refreshedAt = System.currentTimeMillis();
            return null;
        }).whenComplete((v, t) -> refreshing = false);
    }

    /** Waits for a refresh to finish (used right after a cycle starts). */
    public void refreshNow() {
        refreshing = false;
        refresh();
        plugin.db().flush();
    }

    public Map<UUID, Integer> activeUniques() {
        return activeUniques;
    }

    public List<Entry> top() {
        return top;
    }

    public int totalPlayers() {
        return totalPlayers;
    }

    public int activeCount() {
        return activeCount;
    }

    public long refreshedAt() {
        return refreshedAt;
    }
}
