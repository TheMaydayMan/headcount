package com.gradychastain.headcount.points;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.event.PointsChangeEvent;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.player.PlayerData;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Headcount's own currency (not Vault money). Points earned at a cycle's end are spent during the next cycle.
 * Every database change is an atomic increment, so changes made while a player is offline or logging in are never
 * lost; the online cache is updated alongside.
 */
public final class PointsService {
    private final HeadcountPlugin plugin;

    public PointsService(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean allowNegative() {
        return plugin.configs().main().getBool("points.allow-negative", false);
    }

    public long balance(UUID uuid) {
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) return d.points;
        return plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT points FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    public long pending(UUID uuid) {
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) return d.pointsNext;
        return plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT points_next FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    /**
     * Adds (or with a negative delta, takes) points now or for the next cycle.
     *
     * @return the delta actually applied (0 if cancelled)
     */
    public long change(UUID uuid, long delta, boolean nextCycle, String reason, String actor) {
        if (delta == 0) return 0;
        PointsChangeEvent event = new PointsChangeEvent(uuid, delta, nextCycle, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return 0;
        long applied = event.getDelta();
        if (!nextCycle && !allowNegative() && applied < 0) {
            long have = balance(uuid);
            applied = Math.max(applied, -have);
        }
        if (applied == 0) return 0;
        final long d = applied;
        String column = nextCycle ? "points_next" : "points";
        PlayerData data = plugin.players().loaded(uuid);
        if (data != null) {
            if (nextCycle) data.pointsNext += d;
            else data.points += d;
        }
        boolean clampZero = !allowNegative();
        plugin.db().write(c -> {
            String sql = clampZero && !nextCycle
                    ? "UPDATE players SET points = MAX(0, points + ?) WHERE uuid=?"
                    : "UPDATE players SET " + column + " = " + column + " + ? WHERE uuid=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, d);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
        plugin.audit().log(AuditType.POINTS, "player", plugin.players().name(uuid) + "(" + uuid + ")", "delta", d,
                "next-cycle", nextCycle, "reason", reason, "actor", actor);
        return d;
    }

    /** Sets the balance outright (admin). */
    public void set(UUID uuid, long value, String actor) {
        long now = balance(uuid);
        change(uuid, value - now, false, "set", actor);
    }

    /** Takes points for a purchase if the player can afford it. */
    public boolean spend(UUID uuid, long amount, String reason) {
        if (amount <= 0) return true;
        if (balance(uuid) < amount) return false;
        long applied = change(uuid, -amount, false, reason, "shop");
        if (applied != -amount) {
            if (applied != 0) change(uuid, -applied, false, "refund:" + reason, "shop");
            return false;
        }
        PlayerData d = plugin.players().loaded(uuid);
        int cycle = plugin.cycles().currentId();
        if (d != null && d.stats != null) d.stats.pointsSpent += amount;
        if (cycle > 0) plugin.playerStore().addStat(cycle, uuid, "points_spent", amount);
        return true;
    }
}
