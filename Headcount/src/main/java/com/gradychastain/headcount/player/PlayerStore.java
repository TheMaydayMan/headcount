package com.gradychastain.headcount.player;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.gradychastain.headcount.storage.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** SQL for player rows, per-cycle stats, titles and flags. */
public final class PlayerStore {
    private static final Set<String> PLAYER_COLUMNS = Set.of("name", "last_join", "last_quit", "points", "points_next", "streak",
            "last_score", "last_unique", "sidebar", "equipped_title", "setup_cycle", "vault_size", "next_heads_mod", "next_size_mod",
            "rollover_override", "score_bonus", "bedrock", "pvp");
    private static final Set<String> STAT_COLUMNS = Set.of("playtime_ms", "kills", "deaths", "trades", "heads_in", "heads_out",
            "points_spent", "purchases", "active");

    private final Database db;

    public PlayerStore(Database db) {
        this.db = db;
    }

    public Database db() {
        return db;
    }

    /** Loads everything for one player. Runs on the database thread (call through {@link Database#sync}). */
    public PlayerData load(Connection c, UUID uuid, String name, int cycle) throws SQLException {
        PlayerData d = new PlayerData(uuid, name);
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    d.exists = true;
                    d.name = rs.getString("name");
                    d.firstJoin = rs.getLong("first_join");
                    d.lastJoin = rs.getLong("last_join");
                    d.points = rs.getLong("points");
                    d.pointsNext = rs.getLong("points_next");
                    d.streak = rs.getInt("streak");
                    int ls = rs.getInt("last_score");
                    d.lastScore = rs.wasNull() ? null : ls;
                    int lu = rs.getInt("last_unique");
                    d.lastUnique = rs.wasNull() ? null : lu;
                    d.sidebar = rs.getInt("sidebar") != 0;
                    d.equippedTitle = rs.getString("equipped_title");
                    d.setupCycle = rs.getInt("setup_cycle");
                    d.vaultSize = rs.getInt("vault_size");
                    d.nextHeadsMod = rs.getString("next_heads_mod");
                    d.nextSizeMod = rs.getString("next_size_mod");
                    double ro = rs.getDouble("rollover_override");
                    d.rolloverOverride = rs.wasNull() ? null : ro;
                    d.scoreBonus = rs.getInt("score_bonus");
                    d.bedrock = rs.getInt("bedrock") != 0;
                    int pvp = rs.getInt("pvp");
                    d.pvp = rs.wasNull() ? null : pvp != 0;
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT title FROM titles WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) d.titles.add(rs.getString(1));
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT flag FROM flags WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) d.flags.add(rs.getString(1));
            }
        }
        if (cycle > 0) d.stats = loadStats(c, uuid, cycle);
        try (PreparedStatement ps = c.prepareStatement("SELECT entry, cycle, ms FROM purchases WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String entry = rs.getString(1);
                    d.everPurchased.add(entry);
                    if (rs.getInt(2) == cycle) d.purchasesThisCycle.merge(entry, 1, Integer::sum);
                    d.lastPurchase.merge(entry, rs.getLong(3), Math::max);
                }
            }
        }
        return d;
    }

    public CycleStats loadStats(Connection c, UUID uuid, int cycle) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM cycle_players WHERE cycle=? AND uuid=?")) {
            ps.setInt(1, cycle);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                CycleStats s = new CycleStats(cycle);
                s.firstJoinMs = rs.getLong("first_join_ms");
                s.active = rs.getInt("active") != 0;
                s.playtimeMs = rs.getLong("playtime_ms");
                s.kills = rs.getInt("kills");
                s.deaths = rs.getInt("deaths");
                s.trades = rs.getInt("trades");
                s.headsIn = rs.getInt("heads_in");
                s.headsOut = rs.getInt("heads_out");
                s.pointsSpent = rs.getLong("points_spent");
                s.purchases = rs.getInt("purchases");
                return s;
            }
        }
    }

    public void insertPlayer(UUID uuid, String name, long now) {
        db.write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO players(uuid, name, name_lower, first_join, last_join) VALUES (?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, name.toLowerCase(Locale.ROOT));
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
        });
    }

    public void updateName(UUID uuid, String name) {
        db.write(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET name=?, name_lower=? WHERE uuid=?")) {
                ps.setString(1, name);
                ps.setString(2, name.toLowerCase(Locale.ROOT));
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public void set(UUID uuid, String column, Object value) {
        if (!PLAYER_COLUMNS.contains(column)) throw new IllegalArgumentException("column " + column);
        db.write(c -> setNow(c, uuid, column, value));
    }

    public static void setNow(Connection c, UUID uuid, String column, Object value) throws SQLException {
        if (!PLAYER_COLUMNS.contains(column)) throw new IllegalArgumentException("column " + column);
        try (PreparedStatement ps = c.prepareStatement("UPDATE players SET " + column + "=? WHERE uuid=?")) {
            if (value instanceof Boolean b) ps.setInt(1, b ? 1 : 0);
            else ps.setObject(1, value);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void add(UUID uuid, String column, long delta) {
        if (!PLAYER_COLUMNS.contains(column)) throw new IllegalArgumentException("column " + column);
        db.write(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET " + column + "=" + column + "+? WHERE uuid=?")) {
                ps.setLong(1, delta);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public void ensureStats(int cycle, UUID uuid, long firstJoinMs) {
        db.write(c -> ensureStatsNow(c, cycle, uuid, firstJoinMs));
    }

    public static void ensureStatsNow(Connection c, int cycle, UUID uuid, long firstJoinMs) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO cycle_players(cycle, uuid, first_join_ms) VALUES (?,?,?)")) {
            ps.setInt(1, cycle);
            ps.setString(2, uuid.toString());
            ps.setLong(3, firstJoinMs);
            ps.executeUpdate();
        }
    }

    public void addStat(int cycle, UUID uuid, String column, long delta) {
        if (!STAT_COLUMNS.contains(column)) throw new IllegalArgumentException("column " + column);
        db.write(c -> {
            ensureStatsNow(c, cycle, uuid, System.currentTimeMillis());
            try (PreparedStatement ps = c.prepareStatement("UPDATE cycle_players SET " + column + "=" + column + "+? WHERE cycle=? AND uuid=?")) {
                ps.setLong(1, delta);
                ps.setInt(2, cycle);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public void setStat(int cycle, UUID uuid, String column, long value) {
        if (!STAT_COLUMNS.contains(column)) throw new IllegalArgumentException("column " + column);
        db.write(c -> {
            ensureStatsNow(c, cycle, uuid, System.currentTimeMillis());
            try (PreparedStatement ps = c.prepareStatement("UPDATE cycle_players SET " + column + "=? WHERE cycle=? AND uuid=?")) {
                ps.setLong(1, value);
                ps.setInt(2, cycle);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public void saveTexture(UUID uuid, String value, String signature) {
        db.write(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET texture_value=?, texture_signature=? WHERE uuid=?")) {
                ps.setString(1, value);
                ps.setString(2, signature);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public ProfileProperty loadTexture(UUID uuid) {
        return db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT texture_value, texture_signature FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getString(1) != null) return new ProfileProperty("textures", rs.getString(1), rs.getString(2));
                }
            }
            return null;
        });
    }

    public void setFlag(UUID uuid, String flag, boolean on) {
        db.write(c -> {
            try (PreparedStatement ps = c.prepareStatement(on ? "INSERT OR IGNORE INTO flags(uuid, flag) VALUES (?,?)" : "DELETE FROM flags WHERE uuid=? AND flag=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, flag);
                ps.executeUpdate();
            }
        });
    }

    public boolean hasFlagStored(UUID uuid, String flag) {
        return db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM flags WHERE uuid=? AND flag=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, flag);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public void setTitle(UUID uuid, String title, boolean owned, String source) {
        db.write(c -> {
            if (owned) {
                try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO titles(uuid, title, granted_ms, source) VALUES (?,?,?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, title);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setString(4, source);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM titles WHERE uuid=? AND title=?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, title);
                    ps.executeUpdate();
                }
            }
        });
    }

    /** Finds a known player by name (case-insensitive). */
    public UUID findByName(String name) {
        return db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM players WHERE name_lower=? ORDER BY last_join DESC LIMIT 1")) {
                ps.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? UUID.fromString(rs.getString(1)) : null;
                }
            }
        });
    }

    public String nameOf(UUID uuid) {
        return db.sync(c -> nameOfNow(c, uuid));
    }

    public static String nameOfNow(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public List<String> knownNames(String prefix, int limit) {
        return db.sync(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT name FROM players WHERE name_lower LIKE ? ORDER BY last_join DESC LIMIT ?")) {
                ps.setString(1, prefix.toLowerCase(Locale.ROOT).replace("%", "") + "%");
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
            }
            return out;
        });
    }

    public static List<UUID> allPlayersNow(Connection c) throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM players"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
        }
        return out;
    }

    public int countPlayers() {
        return db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM players"); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }
}
