package com.gradychastain.headcount.cycle;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.event.CycleStartEvent;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.head.HeadService;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.vault.VaultManager;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Owns the current cycle: starting, ending on schedule, catching up after downtime, warnings, and admin changes.
 * If the server was offline at a rollover, startup ends the stored cycle and then starts whichever cycle should be
 * running now (IDs stay consecutive; skipped calendar periods are not given IDs).
 */
public final class CycleManager {
    private final HeadcountPlugin plugin;
    private final CycleEndProcessor processor;
    private Cycle current;
    private long pendingFirstStart = -1;
    private boolean processing;
    private final Set<String> firedWarnings = new HashSet<>();

    public CycleManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
        this.processor = new CycleEndProcessor(plugin);
    }

    public Cycle current() {
        return current;
    }

    public int currentId() {
        Cycle c = current;
        return c == null ? 0 : c.id();
    }

    public boolean isProcessing() {
        return processing;
    }

    /** Milliseconds until the first cycle starts, when none has started yet; else -1. */
    public long pendingFirstStart() {
        return current == null ? pendingFirstStart : -1;
    }

    public CycleSchedule schedule() {
        return new CycleSchedule(plugin.configs().main().getSection("cycle"), plugin.getLogger());
    }

    /** Loads the stored cycle. Does not process rollovers (see {@link #catchUp()}). */
    public void load() {
        firedWarnings.clear();
        current = plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, start_ms, end_ms, length_days, vault_size, starting_heads, token FROM cycles WHERE state='active' ORDER BY id DESC LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Cycle(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getString(7));
            }
            return null;
        });
        if (current != null) loadWarnings(current.id());
        else pendingFirstStart = schedule().firstStart(System.currentTimeMillis(), plugin.getLogger());
    }

    private void loadWarnings(int cycle) {
        firedWarnings.clear();
        firedWarnings.addAll(plugin.db().sync(c -> {
            Set<String> out = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT warning FROM cycle_warnings WHERE cycle=?")) {
                ps.setInt(1, cycle);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
            }
            return out;
        }));
    }

    /** Ends any overdue cycle and starts the cycle that should be running now. Called once after startup. */
    public void catchUp() {
        long now = System.currentTimeMillis();
        int guard = 0;
        if (current == null) {
            if (pendingFirstStart >= 0 && now >= pendingFirstStart) {
                CycleSchedule s = schedule();
                long start = pendingFirstStart;
                long end = s.boundaryAfter(start, s.lengthDays());
                while (end <= now && guard++ < 10000) {
                    start = end;
                    end = s.boundaryAfter(start, s.lengthDays());
                }
                startCycle(lastEndedId() + 1, start, end, "startup");
            }
            return;
        }
        if (now >= current.endMs()) {
            plugin.getLogger().info("Cycle " + current.id() + " ended at " + Formats.date(current.endMs()) + " while the server was offline. Ending it now.");
            rollover("catch-up");
        }
    }

    private int lastEndedId() {
        return plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT MAX(id) FROM cycles"); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    /** Called every second. */
    public void tick() {
        if (processing) return;
        long now = System.currentTimeMillis();
        if (current == null) {
            if (pendingFirstStart >= 0 && now >= pendingFirstStart) catchUp();
            return;
        }
        if (now >= current.endMs()) {
            rollover("schedule");
            return;
        }
        checkWarnings(now);
    }

    private void checkWarnings(long now) {
        Cfg warnings = plugin.configs().main().getSection("hooks.warnings");
        long grace = plugin.configs().main().getMillis("hooks.missed-warning-grace", 300_000);
        long remaining = current.endMs() - now;
        for (String id : warnings.keys()) {
            if (firedWarnings.contains(id)) continue;
            Cfg w = warnings.getSection(id);
            if (!w.getBool("enabled", true)) continue;
            long before = w.getMillis("before", -1);
            if (before <= 0 || remaining > before) continue;
            markWarning(id);
            // A warning whose moment passed long ago (server was offline) is skipped instead of firing late.
            if (before - remaining > grace) continue;
            plugin.hooks().runWarning(id, w, current);
        }
    }

    private void markWarning(String id) {
        firedWarnings.add(id);
        int cycle = current.id();
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO cycle_warnings(cycle, warning) VALUES (?,?)")) {
                ps.setInt(1, cycle);
                ps.setString(2, id);
                ps.executeUpdate();
            }
        });
    }

    /** Ends the current cycle and starts the next one aligned to the schedule. */
    private void rollover(String cause) {
        Cycle ending = current;
        if (ending == null) return;
        CycleSchedule s = schedule();
        long now = System.currentTimeMillis();
        long start = ending.endMs();
        long end = s.boundaryAfter(start, s.lengthDays());
        int guard = 0;
        while (end <= now && guard++ < 10000) {
            start = end;
            end = s.boundaryAfter(start, s.lengthDays());
        }
        endCycle(ending, cause);
        startCycle(ending.id() + 1, start, end, cause);
    }

    private void endCycle(Cycle ending, String cause) {
        processing = true;
        plugin.actions().setEndingCycle(ending.id());
        try {
            processor.end(ending, false, cause);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Error while ending cycle " + ending.id() + ". Results may be incomplete; check the audit log.", e);
        } finally {
            plugin.actions().setEndingCycle(-1);
            current = null;
            processing = false;
        }
    }

    private void startCycle(int id, long start, long end, String cause) {
        processing = true;
        Cycle cycle;
        try {
            Cfg vaultCfg = plugin.configs().main().getSection("vault");
            int size = Math.max(1, vaultCfg.getInt("size", 27));
            int startingHeads = Math.max(0, vaultCfg.getInt("starting-heads", 27));
            CycleSchedule s = schedule();
            int lengthDays = (int) Math.max(1, Math.round((end - start) / 86_400_000.0));
            if (lengthDays <= 0) lengthDays = s.lengthDays();
            cycle = new Cycle(id, start, end, lengthDays, size, Math.min(startingHeads, size), Cycle.newToken());
            final Cycle c0 = cycle;
            plugin.db().syncTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO cycles(id, start_ms, end_ms, length_days, vault_size, starting_heads, state, token) VALUES (?,?,?,?,?,?, 'active', ?)")) {
                    ps.setInt(1, c0.id());
                    ps.setLong(2, c0.startMs());
                    ps.setLong(3, c0.endMs());
                    ps.setInt(4, c0.lengthDays());
                    ps.setInt(5, c0.vaultSize());
                    ps.setInt(6, c0.startingHeads());
                    ps.setString(7, c0.token());
                    ps.executeUpdate();
                }
            });
            current = cycle;
            pendingFirstStart = -1;
            firedWarnings.clear();
            plugin.audit().log(AuditType.CYCLE_START, "cycle", id, "start", Formats.date(start), "end", Formats.date(end),
                    "vault-size", cycle.vaultSize(), "starting-heads", cycle.startingHeads(), "cause", cause);
            int filled = plugin.vaults().fillAllForCycle(cycle);
            plugin.getLogger().info("Cycle " + id + " started (ends " + Formats.date(end) + "); " + filled + " vaults filled.");
            plugin.kills().resetCycle();
            plugin.shop().resetCycle();
            plugin.refresher().refreshOnline();
            plugin.stats().refreshNow();
        } catch (RuntimeException e) {
            processing = false;
            plugin.getLogger().log(Level.SEVERE, "Error while starting cycle " + id, e);
            return;
        }
        processing = false;
        try {
            Bukkit.getPluginManager().callEvent(new CycleStartEvent(cycle.id(), cycle.startMs(), cycle.endMs()));
            plugin.hooks().runCycleStart(cycle);
            plugin.players().onCycleStarted(cycle);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Error in cycle start hooks", e);
        }
    }

    // ---------------------------------------------------------------- admin

    /** Ends the current cycle now and starts the next one immediately (its end follows the schedule from today). */
    public boolean forceEnd(String actor) {
        if (current == null || processing) return false;
        plugin.audit().log(AuditType.ADMIN, "action", "cycle-force-end", "cycle", current.id(), "actor", actor);
        Cycle ending = current;
        CycleSchedule s = schedule();
        long now = System.currentTimeMillis();
        endCycle(ending, "forced by " + actor);
        startCycle(ending.id() + 1, now, s.boundaryAfter(now, s.lengthDays()), "forced by " + actor);
        return true;
    }

    /** Starts a cycle now when none is running. */
    public boolean forceStart(String actor) {
        if (current != null || processing) return false;
        CycleSchedule s = schedule();
        long now = System.currentTimeMillis();
        plugin.audit().log(AuditType.ADMIN, "action", "cycle-force-start", "actor", actor);
        startCycle(lastEndedId() + 1, now, s.boundaryAfter(now, s.lengthDays()), "forced by " + actor);
        return true;
    }

    public boolean setEnd(long endMs, String actor) {
        if (current == null || processing || endMs <= current.startMs()) return false;
        Cycle old = current;
        current = new Cycle(old.id(), old.startMs(), endMs, old.lengthDays(), old.vaultSize(), old.startingHeads(), old.token());
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cycles SET end_ms=? WHERE id=?")) {
                ps.setLong(1, endMs);
                ps.setInt(2, old.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM cycle_warnings WHERE cycle=?")) {
                ps.setInt(1, old.id());
                ps.executeUpdate();
            }
        });
        firedWarnings.clear();
        // Warnings already past the new end time are marked so they do not all fire at once.
        Cfg warnings = plugin.configs().main().getSection("hooks.warnings");
        long remaining = endMs - System.currentTimeMillis();
        for (String id : warnings.keys()) {
            long before = warnings.getSection(id).getMillis("before", -1);
            if (before > 0 && remaining <= before) markWarning(id);
        }
        plugin.audit().log(AuditType.ADMIN, "action", "cycle-set-end", "cycle", old.id(), "from", Formats.date(old.endMs()),
                "to", Formats.date(endMs), "actor", actor);
        return true;
    }

    /**
     * /hca reset cycle: starts the running cycle over under the same ID, as if it had just started. Nothing is scored.
     * It gets a new token, so every head made before stops counting: it goes defunct, or with {@code deleteHeads} it
     * is removed wherever Headcount sees it. Vaults are refilled with the current vault settings; kills, activity and
     * playtime, shop limits and stock, warnings and per-player cycle-start hooks waiting for offline players start
     * over; the cycle runs from now to the next scheduled end. Points, titles, streaks, history and effects are kept.
     */
    public boolean restart(String actor, boolean deleteHeads) {
        if (current == null || processing) return false;
        Cycle old = current;
        Cycle fresh;
        processing = true;
        try {
            plugin.trades().cancelAll("cycle-reset");
            plugin.guis().closeAll();
            plugin.vaults().closeAllViews();
            plugin.players().flushPlaytime();
            plugin.db().flush();
            Cfg vaultCfg = plugin.configs().main().getSection("vault");
            int size = Math.max(1, vaultCfg.getInt("size", 27));
            int startingHeads = Math.max(0, vaultCfg.getInt("starting-heads", 27));
            CycleSchedule s = schedule();
            long now = System.currentTimeMillis();
            long end = s.boundaryAfter(now, s.lengthDays());
            int lengthDays = (int) Math.max(1, Math.round((end - now) / 86_400_000.0));
            fresh = new Cycle(old.id(), now, end, lengthDays, size, Math.min(startingHeads, size), Cycle.newToken());
            final Cycle f0 = fresh;
            VaultManager.Fill[] fill = new VaultManager.Fill[1];
            plugin.db().syncTransaction(c -> {
                HeadService.voidRun(c, old.id(), old.token(), now, "cycle-reset", deleteHeads);
                try (PreparedStatement ps = c.prepareStatement("UPDATE cycles SET start_ms=?, end_ms=?, length_days=?, vault_size=?, starting_heads=?, token=? WHERE id=?")) {
                    ps.setLong(1, f0.startMs());
                    ps.setLong(2, f0.endMs());
                    ps.setInt(3, f0.lengthDays());
                    ps.setInt(4, f0.vaultSize());
                    ps.setInt(5, f0.startingHeads());
                    ps.setString(6, f0.token());
                    ps.setInt(7, f0.id());
                    ps.executeUpdate();
                }
                for (String table : new String[]{"kill_drops", "cycle_players", "cycle_warnings", "shop_stock", "pending_hooks"}) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE cycle=?")) {
                        ps.setInt(1, f0.id());
                        ps.executeUpdate();
                    }
                }
                // Purchases stay on record (cooldowns and "bought before" requirements still see them) but no longer
                // count toward this cycle's limits: a negative cycle means "cycle N, before it was reset".
                try (PreparedStatement ps = c.prepareStatement("UPDATE purchases SET cycle=? WHERE cycle=?")) {
                    ps.setInt(1, -f0.id());
                    ps.setInt(2, f0.id());
                    ps.executeUpdate();
                }
                fill[0] = plugin.vaults().refillForRestart(c, f0);
            });
            current = fresh;
            firedWarnings.clear();
            plugin.heads().loadVoided();
            plugin.audit().log(AuditType.ADMIN, "action", "cycle-reset", "cycle", old.id(), "old-token", old.token(), "new-token", fresh.token(), "old-heads", deleteHeads ? "delete" : "defunct", "actor", actor);
            plugin.audit().log(AuditType.CYCLE_START, "cycle", fresh.id(), "start", Formats.date(fresh.startMs()), "end", Formats.date(fresh.endMs()),
                    "vault-size", fresh.vaultSize(), "starting-heads", fresh.startingHeads(), "cause", "reset by " + actor);
            int filled = plugin.vaults().afterFill(fresh, fill[0]);
            plugin.getLogger().info("Cycle " + fresh.id() + " was reset by " + actor + " and started over (ends " + Formats.date(fresh.endMs()) + "); " + filled + " vaults refilled.");
            plugin.kills().resetCycle();
            plugin.shop().resetCycle();
            plugin.refresher().refreshOnline();
            plugin.stats().refreshNow();
        } catch (RuntimeException e) {
            processing = false;
            plugin.getLogger().log(Level.SEVERE, "Error while resetting cycle " + old.id(), e);
            return false;
        }
        processing = false;
        try {
            Bukkit.getPluginManager().callEvent(new CycleStartEvent(fresh.id(), fresh.startMs(), fresh.endMs()));
            plugin.hooks().runCycleStart(fresh);
            plugin.players().onCycleStarted(fresh);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Error in cycle start hooks", e);
        }
        return true;
    }

    public CycleResults simulate() {
        if (current == null) return null;
        return processor.end(current, true, "simulation");
    }
}
