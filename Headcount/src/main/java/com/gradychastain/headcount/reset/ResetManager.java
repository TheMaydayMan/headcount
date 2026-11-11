package com.gradychastain.headcount.reset;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.head.HeadService;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /hca reset hard: puts Headcount back to how it was when first installed, keeping the config files. Every player,
 * cycle, vault, point, title, result and effect is deleted, every head made so far stops counting (and is removed
 * wherever Headcount sees it, or only goes defunct), and the first cycle starts again as on a new install. It takes a number of typed
 * confirmations (reset.hard.confirmations), each with a new random code, and the headcount.admin.reset.hard
 * permission (the server console always has it). The database, and optionally the audit log, are kept in
 * plugins/Headcount/backups/hard-reset-&lt;time&gt;/ first.
 */
public final class ResetManager {
    private record Pending(String code, int step, long expires, boolean deleteHeads) {}

    /** Tables emptied by a hard reset. active_actions is handled separately; meta and void_heads are kept. */
    private static final List<String> WIPED = List.of("players", "cycles", "cycle_players", "vault_items", "heads", "kill_drops",
            "results", "titles", "purchases", "shop_stock", "pending_hooks", "pending_actions", "pending_heads", "flags", "cycle_warnings", "included_permissions");

    private final HeadcountPlugin plugin;
    private final Map<String, Pending> pending = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public ResetManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private Cfg cfg() {
        return plugin.configs().main().getSection("reset.hard");
    }

    private static String key(CommandSender s) {
        return s instanceof Player p ? p.getUniqueId().toString() : "console:" + s.getName();
    }

    public static boolean mayHardReset(CommandSender s) {
        return s instanceof ConsoleCommandSender || s.hasPermission("headcount.admin.reset.hard");
    }

    private String newCode() {
        return String.format("%04d", random.nextInt(10_000));
    }

    /** Whether a reset deletes the old heads: "delete-heads" / "keep-heads" in the command, else reset.delete-old-heads. */
    public boolean deleteHeads(String[] args) {
        for (String a : args) {
            if (a.equalsIgnoreCase("delete-heads")) return true;
            if (a.equalsIgnoreCase("keep-heads")) return false;
        }
        return plugin.configs().main().getBool("reset.delete-old-heads", true);
    }

    /**
     * /hca reset hard [delete-heads|keep-heads], then /hca reset hard &lt;code&gt; for each confirmation.
     * @param code the typed code, or null to start
     */
    public void hardCommand(CommandSender s, String code, boolean deleteHeads, String actor) {
        if (!cfg().getBool("enabled", true)) {
            plugin.lang().send(s, "admin.reset.hard-disabled");
            return;
        }
        if (!mayHardReset(s)) {
            plugin.lang().send(s, "general.no-permission");
            return;
        }
        int steps = Math.max(1, cfg().getInt("confirmations", 3));
        long timeout = Math.max(5_000, cfg().getMillis("confirm-timeout", 60_000));
        long now = System.currentTimeMillis();
        String k = key(s);
        if (code == null) {
            Pending p = new Pending(newCode(), 1, now + timeout, deleteHeads);
            pending.put(k, p);
            plugin.lang().send(s, "admin.reset.hard-warning", summary().mini("old_heads",
                    plugin.lang().raw(deleteHeads ? "admin.reset.old-heads-delete" : "admin.reset.old-heads-keep")));
            step(s, p, steps, timeout);
            plugin.audit().log(AuditType.ADMIN, "action", "hard-reset-requested", "actor", actor);
            return;
        }
        Pending p = pending.remove(k);
        if (p == null || now > p.expires() || !p.code().equals(code)) {
            plugin.lang().send(s, p != null && now > p.expires() ? "admin.reset.hard-expired" : "admin.reset.hard-wrong-code");
            return;
        }
        if (p.step() < steps) {
            Pending next = new Pending(newCode(), p.step() + 1, now + timeout, p.deleteHeads());
            pending.put(k, next);
            step(s, next, steps, timeout);
            return;
        }
        if (plugin.cycles().isProcessing()) {
            plugin.lang().send(s, "admin.cycle.cannot");
            return;
        }
        String backup = hardReset(actor, p.deleteHeads());
        plugin.lang().send(s, "admin.reset.hard-done", "backup", backup == null ? "none" : backup);
    }

    private void step(CommandSender s, Pending p, int steps, long timeout) {
        Vars v = Vars.of("step", p.step(), "steps", steps, "code", p.code(), "seconds", timeout / 1000);
        plugin.lang().send(s, p.step() == steps ? "admin.reset.hard-final" : "admin.reset.hard-step", v);
    }

    /** What a hard reset would delete, for the first warning. */
    private Vars summary() {
        return plugin.db().sync(c -> {
            Vars v = Vars.empty();
            String[][] counts = {{"players", "SELECT COUNT(*) FROM players"}, {"cycles", "SELECT COUNT(*) FROM cycles"},
                    {"heads", "SELECT COUNT(*) FROM vault_items"}, {"results", "SELECT COUNT(*) FROM results"},
                    {"titles", "SELECT COUNT(*) FROM titles"}, {"effects", "SELECT COUNT(*) FROM active_actions"},
                    {"points", "SELECT COALESCE(SUM(points + points_next), 0) FROM players"}};
            for (String[] q : counts) {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q[1])) {
                    v.put(q[0], rs.next() ? rs.getLong(1) : 0);
                }
            }
            return v;
        });
    }

    /**
     * Runs the hard reset now (no confirmations). @param deleteHeads delete every old head where seen, else they go
     * defunct @return the backup folder's name, or null when backups are off
     */
    public String hardReset(String actor, boolean deleteHeads) {
        long started = System.currentTimeMillis();
        String stamp = LocalDateTime.now(Formats.zone()).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File folder = new File(plugin.getDataFolder(), "backups/hard-reset-" + stamp);
        boolean backup = cfg().getBool("backup-database", true);
        boolean archiveAudit = cfg().getBool("archive-audit-log", true);
        plugin.audit().log(AuditType.ADMIN, "action", "hard-reset", "actor", actor, "old-heads", deleteHeads ? "delete" : "defunct", "backup", backup ? folder.getName() : null);
        plugin.getLogger().warning("Hard reset by " + actor + ": deleting all Headcount data" + (backup ? " (backup in backups/" + folder.getName() + ")" : "") + ".");

        // Undo what is live in the world first, while the data still says what to undo.
        plugin.trades().cancelAll("hard-reset");
        plugin.guis().closeAll();
        plugin.vaults().closeAllViews();
        int effects = plugin.actions().expireAll("hard-reset");
        plugin.titles().unequipAll();
        plugin.players().flushPlaytime();
        plugin.db().flush();

        if (backup) {
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create " + folder);
            File copy = new File(folder, plugin.db().file().getName());
            plugin.db().sync(c -> {
                try (Statement st = c.createStatement()) {
                    st.execute("VACUUM INTO '" + copy.getAbsolutePath().replace("'", "''") + "'");
                }
                return null;
            });
        }

        long now = System.currentTimeMillis();
        plugin.db().syncTransaction(c -> {
            // Every cycle run so far is undone, so all of its heads become worthless.
            List<Object[]> runs = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, token FROM cycles"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) runs.add(new Object[]{rs.getInt(1), rs.getString(2)});
            }
            for (Object[] r : runs) HeadService.voidRun(c, (Integer) r[0], (String) r[1], now, "hard-reset", deleteHeads);
            for (String table : WIPED) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("DELETE FROM " + table);
                }
            }
            // Effects that can only be undone with their player online stay until that player joins and gets them undone.
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM active_actions WHERE state <> 'PENDING_REVERT'");
            }
        });
        plugin.heads().loadVoided();

        // Start over in memory exactly as the plugin does on a first start.
        plugin.players().forgetAll();
        plugin.vaults().onCycleWiped();
        plugin.cycles().load();
        plugin.vaults().loadIndex();
        plugin.kills().load();
        plugin.actions().loadActive();
        plugin.shop().load(plugin.configs().shop());
        plugin.cycles().catchUp();
        plugin.stats().refreshNow();
        plugin.players().loadOnline();   // everyone online joins as a new player
        plugin.integrations().refreshIncludedPermissions();
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.statuses().invalidate(p.getUniqueId());
            plugin.sidebar().resync(p);
        }
        plugin.refresher().refreshOnline();

        int archived = archiveAudit ? plugin.audit().archive(new File(folder, "audit")) : 0;
        var cycle = plugin.cycles().current();
        plugin.audit().log(AuditType.ADMIN, "action", "hard-reset-done", "actor", actor, "effects-ended", effects,
                "audit-files-archived", archived, "backup", backup ? folder.getName() : null, "cycle", cycle == null ? null : cycle.id());
        plugin.getLogger().warning("Hard reset done in " + (System.currentTimeMillis() - started) + " ms. "
                + (cycle == null ? "No cycle is running yet." : "Cycle " + cycle.id() + " started (ends " + Formats.date(cycle.endMs()) + ")."));
        for (Player p : Bukkit.getOnlinePlayers()) plugin.lang().send(p, "admin.reset.hard-broadcast");
        return backup || archived > 0 ? "backups/" + folder.getName() : null;
    }
}
