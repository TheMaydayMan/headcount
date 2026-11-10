package com.gradychastain.headcount.cycle;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.event.CycleEndEvent;
import com.gradychastain.headcount.api.event.ScoreCalculatedEvent;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.player.CycleStats;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.player.PlayerStore;
import com.gradychastain.headcount.scoring.Population;
import com.gradychastain.headcount.scoring.ScoreInputs;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Json;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The end-of-cycle pipeline: tally each vault's unique heads, score every active player, update streaks, roll over
 * and award points, apply outcomes, store results, then delete every head in every vault. Inactive players get no
 * score, points or outcomes (their unspent points still roll over by the configured percent).
 */
final class CycleEndProcessor {
    private record Row(UUID uuid, String name, long points, long pointsNext, int streak, Integer lastScore, Integer lastUnique,
                       Double rollover, int bonus, int vaultSize, int titles) {}

    private record Snapshot(Map<UUID, Row> players, Map<UUID, int[]> tallies, Map<UUID, CycleStats> stats,
                            List<String[]> heads) {}

    private final HeadcountPlugin plugin;

    CycleEndProcessor(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    CycleResults end(Cycle cycle, boolean simulate, String cause) {
        if (!simulate) {
            plugin.trades().cancelAll("cycle-end");
            plugin.vaults().closeAllViews();
        }
        plugin.players().flushPlaytime();
        plugin.vaults().flushAll();
        plugin.db().flush();
        Snapshot snap = plugin.db().sync(c -> read(c, cycle.id(), !simulate));

        Cfg scoring = plugin.configs().scoring();
        Cfg points = plugin.configs().main().getSection("points");
        String inactiveStreak = scoring.getString("streaks.inactive", "pause").toLowerCase(Locale.ROOT);
        String zeroStreak = scoring.getString("streaks.zero-score", "break").toLowerCase(Locale.ROOT);
        double defaultRollover = points.getDouble("rollover-percent", 0);
        double earnMultiplier = points.getDouble("earn-multiplier", 1.0);

        List<Integer> activeUniques = new ArrayList<>();
        for (Map.Entry<UUID, CycleStats> e : snap.stats().entrySet()) {
            if (!e.getValue().active) continue;
            int[] t = snap.tallies().get(e.getKey());
            activeUniques.add(t == null ? 0 : t[0]);
        }
        Population pop = new Population(activeUniques, snap.players().size(), plugin.scores().denseRanks());

        Map<UUID, PlayerResult> results = new LinkedHashMap<>();
        int rewarded = 0, punished = 0;
        double scoreSum = 0;
        for (Row row : snap.players().values()) {
            CycleStats st = snap.stats().get(row.uuid());
            boolean active = st != null && st.active;
            int[] t = snap.tallies().getOrDefault(row.uuid(), new int[]{0, 0, 0});
            Integer score = null;
            Integer rank = null;
            Double percentile = null;
            if (active) {
                ScoreInputs in = new ScoreInputs(row.uuid(), row.name(), true, t[0], t[1], t[2], st.kills, st.deaths, st.trades,
                        st.headsIn, st.headsOut, st.purchases, st.pointsSpent, st.playtimeMs, row.streak(), row.lastScore(),
                        row.lastUnique(), row.points(), row.pointsNext(), row.bonus(), row.vaultSize(), row.titles());
                Map<String, Double> vars = plugin.scores().variables(in, pop, cycle);
                OfflinePlayer op = Bukkit.getOfflinePlayer(row.uuid());
                int s = plugin.scores().score(vars, op);
                ScoreCalculatedEvent ev = new ScoreCalculatedEvent(row.uuid(), cycle.id(), s, Map.copyOf(vars), simulate);
                Bukkit.getPluginManager().callEvent(ev);
                s = ev.getScore();
                score = s;
                rank = pop.rank(t[0]);
                percentile = pop.percentile(t[0]);
                scoreSum += s;
                if (s > 0) rewarded++;
            }
            // Streaks: consecutive positive or negative cycles.
            int streak = row.streak();
            if (!active) {
                if (inactiveStreak.equals("break")) streak = 0;
            } else if (score > 0) {
                streak = streak > 0 ? streak + 1 : 1;
            } else if (score < 0) {
                streak = streak < 0 ? streak - 1 : -1;
            } else if (!zeroStreak.equals("pause")) {
                streak = 0;
            }
            // Points: a percent of unspent points rolls over, the rest are gone; positive scores earn points.
            double pct = row.rollover() != null ? row.rollover() : defaultRollover;
            pct = Math.max(0, Math.min(100, pct));
            long rolled = (long) Math.floor(Math.max(0, row.points()) * pct / 100.0);
            long earned = active && score > 0 ? (long) Math.floor(score * earnMultiplier) : 0;
            long after = rolled + earned + row.pointsNext();
            String tier = active ? plugin.shop().tierNameFor(score) : "";
            results.put(row.uuid(), new PlayerResult(row.uuid(), row.name(), active, t[0], t[1], t[2], score, earned,
                    st == null ? 0 : st.pointsSpent, row.points(), after, rolled, streak, rank, percentile, List.of(),
                    st == null ? 0 : st.playtimeMs, st == null ? 0 : st.kills, st == null ? 0 : st.deaths, st == null ? 0 : st.trades, tier));
        }

        List<PlayerResult> ranked = new ArrayList<>(results.values().stream().filter(PlayerResult::active).toList());
        ranked.sort((a, b) -> a.unique() != b.unique() ? Integer.compare(b.unique(), a.unique()) : a.name().compareToIgnoreCase(b.name()));
        int activeCount = ranked.size();
        double avg = activeCount == 0 ? 0 : scoreSum / activeCount;

        if (simulate) {
            for (PlayerResult r : ranked) {
                if (r.score() != null && plugin.outcomes().wouldPunish(r)) punished++;
            }
            return new CycleResults(cycle, results, ranked, activeCount, snap.players().size(), avg, rewarded, punished, true);
        }

        // Persist new balances, streaks and last scores before outcomes run, so outcome actions see the new state.
        plugin.db().syncTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET points=?, points_next=0, streak=?, last_score=?, last_unique=?, rollover_override=NULL, score_bonus=0 WHERE uuid=?")) {
                for (PlayerResult r : results.values()) {
                    ps.setLong(1, r.pointsAfter());
                    ps.setInt(2, r.streak());
                    if (r.score() == null) ps.setNull(3, java.sql.Types.INTEGER);
                    else ps.setInt(3, r.score());
                    if (r.active()) ps.setInt(4, r.unique());
                    else ps.setNull(4, java.sql.Types.INTEGER);
                    ps.setString(5, r.uuid().toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
        for (PlayerResult r : results.values()) {
            PlayerData d = plugin.players().loaded(r.uuid());
            if (d == null) continue;
            d.points = r.pointsAfter();
            d.pointsNext = 0;
            d.streak = r.streak();
            d.lastScore = r.score();
            d.lastUnique = r.active() ? r.unique() : null;
            d.rolloverOverride = null;
            d.scoreBonus = 0;
        }

        // Outcomes for active players.
        for (PlayerResult r : ranked) {
            List<String> applied = plugin.outcomes().apply(r, cycle);
            PlayerResult withOutcomes = r.withOutcomes(applied);
            results.put(r.uuid(), withOutcomes);
            if (plugin.outcomes().anyPunishment(applied)) punished++;
        }
        // Outcome actions may have changed balances; history records the balance players really start the next cycle with.
        plugin.db().flush();
        Map<UUID, Long> balances = plugin.db().sync(c -> {
            Map<UUID, Long> out = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid, points FROM players"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
            }
            return out;
        });
        for (Map.Entry<UUID, PlayerResult> e : new ArrayList<>(results.entrySet())) {
            Long b = balances.get(e.getKey());
            if (b != null) results.put(e.getKey(), e.getValue().withPointsAfter(b));
        }
        List<PlayerResult> finalRanked = new ArrayList<>(results.values().stream().filter(PlayerResult::active).toList());
        finalRanked.sort((a, b) -> a.unique() != b.unique() ? Integer.compare(b.unique(), a.unique()) : a.name().compareToIgnoreCase(b.name()));
        CycleResults out = new CycleResults(cycle, results, finalRanked, activeCount, snap.players().size(), avg, rewarded, punished, false);

        store(out, snap, cause);
        plugin.actions().onCycleEnd(cycle.id());
        Bukkit.getPluginManager().callEvent(new CycleEndEvent(out));
        try {
            plugin.hooks().runCycleEnd(out);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error in cycle end hooks", e);
        }
        return out;
    }

    private Snapshot read(Connection c, int cycle, boolean readHeads) throws SQLException {
        Map<UUID, Row> players = new LinkedHashMap<>();
        Map<UUID, Integer> titles = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, COUNT(*) FROM titles GROUP BY uuid"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) titles.put(UUID.fromString(rs.getString(1)), rs.getInt(2));
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, points, points_next, streak, last_score, last_unique, rollover_override, score_bonus, vault_size FROM players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString(1));
                int ls = rs.getInt(6);
                Integer lastScore = rs.wasNull() ? null : ls;
                int lu = rs.getInt(7);
                Integer lastUnique = rs.wasNull() ? null : lu;
                double ro = rs.getDouble(8);
                Double rollover = rs.wasNull() ? null : ro;
                players.put(id, new Row(id, rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5), lastScore, lastUnique, rollover,
                        rs.getInt(9), rs.getInt(10), titles.getOrDefault(id, 0)));
            }
        }
        Map<UUID, int[]> tallies = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, COUNT(DISTINCT head_owner), COUNT(*), SUM(CASE WHEN head_owner = uuid THEN 1 ELSE 0 END) FROM vault_items WHERE cycle=? GROUP BY uuid")) {
            ps.setInt(1, cycle);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tallies.put(UUID.fromString(rs.getString(1)), new int[]{rs.getInt(2), rs.getInt(3), rs.getInt(4)});
            }
        }
        Map<UUID, CycleStats> stats = new HashMap<>();
        PlayerStore store = plugin.playerStore();
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM cycle_players WHERE cycle=?")) {
            ps.setInt(1, cycle);
            try (ResultSet rs = ps.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rs.next()) ids.add(UUID.fromString(rs.getString(1)));
                for (UUID id : ids) {
                    CycleStats s = store.loadStats(c, id, cycle);
                    if (s != null) stats.put(id, s);
                }
            }
        }
        List<String[]> heads = new ArrayList<>();
        if (readHeads) {
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid, head_id, head_owner FROM vault_items WHERE cycle=?")) {
                ps.setInt(1, cycle);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) heads.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }
            }
        }
        return new Snapshot(players, tallies, stats, heads);
    }

    private void store(CycleResults out, Snapshot snap, String cause) {
        Cycle cycle = out.cycle();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("active", out.active());
        summary.put("players", out.totalPlayers());
        summary.put("average_score", out.averageScore());
        summary.put("rewarded", out.rewarded());
        summary.put("punished", out.punished());
        List<Map<String, Object>> top = new ArrayList<>();
        for (PlayerResult r : out.top(10)) top.add(Map.of("uuid", r.uuid().toString(), "name", r.name(), "unique", r.unique(), "score", r.score()));
        summary.put("top", top);
        String summaryJson = Json.write(summary);
        long now = System.currentTimeMillis();
        plugin.db().syncTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT OR REPLACE INTO results(cycle, uuid, name, active, unique_heads, total_heads, own_heads, score, points_earned,
                      points_spent, points_before, points_after, rolled_over, streak, rank, percentile, outcomes, playtime_ms, kills, deaths, trades)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")) {
                for (PlayerResult r : out.players().values()) {
                    ps.setInt(1, cycle.id());
                    ps.setString(2, r.uuid().toString());
                    ps.setString(3, r.name());
                    ps.setInt(4, r.active() ? 1 : 0);
                    ps.setInt(5, r.unique());
                    ps.setInt(6, r.total());
                    ps.setInt(7, r.own());
                    if (r.score() == null) ps.setNull(8, java.sql.Types.INTEGER);
                    else ps.setInt(8, r.score());
                    ps.setLong(9, r.pointsEarned());
                    ps.setLong(10, r.pointsSpent());
                    ps.setLong(11, r.pointsBefore());
                    ps.setLong(12, r.pointsAfter());
                    ps.setLong(13, r.rolledOver());
                    ps.setInt(14, r.streak());
                    if (r.rank() == null) ps.setNull(15, java.sql.Types.INTEGER);
                    else ps.setInt(15, r.rank());
                    if (r.percentile() == null) ps.setNull(16, java.sql.Types.REAL);
                    else ps.setDouble(16, r.percentile());
                    ps.setString(17, String.join(",", r.outcomes()));
                    ps.setLong(18, r.playtimeMs());
                    ps.setInt(19, r.kills());
                    ps.setInt(20, r.deaths());
                    ps.setInt(21, r.trades());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE cycles SET state='ended', ended_ms=?, summary=? WHERE id=?")) {
                ps.setLong(1, now);
                ps.setString(2, summaryJson);
                ps.setInt(3, cycle.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM vault_items WHERE cycle=?")) {
                ps.setInt(1, cycle.id());
                ps.executeUpdate();
            }
            // Heads from older cycles should never be in a vault, but clear any strays as well.
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM vault_items WHERE cycle<?")) {
                ps.setInt(1, cycle.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM kill_drops WHERE cycle<?")) {
                ps.setInt(1, cycle.id());
                ps.executeUpdate();
            }
        });
        plugin.vaults().onCycleWiped();
        for (String[] h : snap.heads()) {
            plugin.audit().log(AuditType.HEAD_DELETE, "head", h[1], "owner", h[2], "vault", h[0], "cycle", cycle.id(), "reason", "cycle-end");
        }
        for (PlayerResult r : out.players().values()) {
            if (!r.active()) continue;
            plugin.audit().log(AuditType.SCORE, "cycle", cycle.id(), "player", r.name() + "(" + r.uuid() + ")", "unique", r.unique(),
                    "total", r.total(), "own", r.own(), "score", r.score(), "rank", r.rank(), "earned", r.pointsEarned(),
                    "rolled-over", r.rolledOver(), "points", r.pointsAfter(), "streak", r.streak(), "outcomes", String.join(",", r.outcomes()));
        }
        plugin.audit().log(AuditType.CYCLE_END, "cycle", cycle.id(), "active", out.active(), "players", out.totalPlayers(),
                "average-score", String.format(Locale.ROOT, "%.2f", out.averageScore()), "rewarded", out.rewarded(),
                "punished", out.punished(), "heads-deleted", snap.heads().size(), "cause", cause);
    }
}
