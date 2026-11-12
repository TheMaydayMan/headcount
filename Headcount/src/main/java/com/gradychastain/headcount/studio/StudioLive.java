package com.gradychastain.headcount.studio;

import org.sqlite.SQLiteConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Studio's live pages: the database read through its own read-only connection (Headcount's own connection is never
 * touched, and SQLite's WAL mode lets both read at once), and the audit log files searched as text.
 */
final class StudioLive {
    private static final Pattern AUDIT_LINE = Pattern.compile("^(\\S+)\\s+([A-Z_]+)\\s*(.*)$");

    private final Supplier<File> dbFile;
    private final Supplier<File> auditFolder;

    StudioLive(Supplier<File> dbFile, Supplier<File> auditFolder) {
        this.dbFile = dbFile;
        this.auditFolder = auditFolder;
    }

    boolean available() {
        return dbFile.get().isFile();
    }

    /** Thrown when there is no database yet; shown to the page as a 404. */
    static final class Missing extends RuntimeException {
        Missing(String m) {
            super(m);
        }
    }

    private Connection open() throws SQLException {
        File f = dbFile.get();
        if (!f.isFile()) throw new Missing("No Headcount database at " + f.getPath());
        SQLiteConfig cfg = new SQLiteConfig();
        cfg.setReadOnly(true);
        cfg.setBusyTimeout(3000);
        return DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath(), cfg.toProperties());
    }

    private static List<Map<String, Object>> rows(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                    out.add(row);
                }
                return out;
            }
        }
    }

    private static Map<String, Object> one(Connection c, String sql, Object... params) throws SQLException {
        List<Map<String, Object>> r = rows(c, sql, params);
        return r.isEmpty() ? null : r.getFirst();
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static boolean truthy(Object o) {
        return o instanceof Number n ? n.longValue() != 0 : o instanceof Boolean b && b;
    }

    // ---------------------------------------------------------------- cycle and players

    private Map<String, Object> cycle(Connection c) throws SQLException {
        boolean token = rows(c, "PRAGMA table_info(cycles)").stream().anyMatch(r -> "token".equals(r.get("name")));
        return one(c, "SELECT id, start_ms, end_ms, length_days, vault_size, starting_heads" + (token ? ", token" : "")
                + " FROM cycles WHERE state='active' ORDER BY id DESC LIMIT 1");
    }

    private List<Map<String, Object>> players(Connection c, Object cycleId) throws SQLException {
        Object cid = cycleId == null ? -1 : cycleId;
        Map<Object, Map<String, Object>> counts = new HashMap<>();
        for (Map<String, Object> r : rows(c, "SELECT uuid, COUNT(DISTINCT head_owner) AS u, COUNT(*) AS t, "
                + "SUM(CASE WHEN head_owner=uuid THEN 1 ELSE 0 END) AS o FROM vault_items WHERE cycle=? GROUP BY uuid", cid)) {
            counts.put(r.get("uuid"), r);
        }
        Map<Object, Map<String, Object>> stats = new HashMap<>();
        for (Map<String, Object> r : rows(c, "SELECT * FROM cycle_players WHERE cycle=?", cid)) stats.put(r.get("uuid"), r);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : rows(c, "SELECT * FROM players")) {
            Map<String, Object> ct = counts.get(p.get("uuid"));
            Map<String, Object> s = stats.getOrDefault(p.get("uuid"), Map.of());
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("uuid", p.get("uuid"));
            o.put("name", p.get("name"));
            o.put("points", p.get("points"));
            o.put("points_next", p.get("points_next"));
            o.put("streak", p.get("streak"));
            o.put("last_score", p.get("last_score"));
            o.put("last_join", p.get("last_join"));
            o.put("last_quit", p.get("last_quit"));
            o.put("online", num(p.get("last_join")) > num(p.get("last_quit")));
            o.put("vault_size", p.get("vault_size"));
            o.put("score_bonus", p.get("score_bonus"));
            o.put("bedrock", truthy(p.get("bedrock")));
            o.put("equipped_title", p.get("equipped_title"));
            o.put("unique", ct == null ? 0 : num(ct.get("u")));
            o.put("total", ct == null ? 0 : num(ct.get("t")));
            o.put("own", ct == null ? 0 : num(ct.get("o")));
            o.put("active", truthy(s.get("active")));
            o.put("playtime_ms", s.getOrDefault("playtime_ms", 0));
            o.put("kills", s.getOrDefault("kills", 0));
            o.put("deaths", s.getOrDefault("deaths", 0));
            o.put("trades", s.getOrDefault("trades", 0));
            out.add(o);
        }
        return out;
    }

    Map<String, Object> summary() throws SQLException {
        Map<String, Object> cyc;
        List<Map<String, Object>> players;
        List<Object> warnings = new ArrayList<>();
        long effects;
        try (Connection c = open()) {
            cyc = cycle(c);
            players = players(c, cyc == null ? null : cyc.get("id"));
            if (cyc != null) for (Map<String, Object> r : rows(c, "SELECT warning FROM cycle_warnings WHERE cycle=?", cyc.get("id"))) warnings.add(r.get("warning"));
            effects = num(one(c, "SELECT COUNT(*) AS n FROM active_actions").get("n"));
        }
        List<Map<String, Object>> recent = new ArrayList<>();
        List<File> files = auditFiles();
        for (File f : files.subList(0, Math.min(2, files.size()))) {
            List<String> lines = readLines(f);
            for (int i = lines.size() - 1; i >= 0 && recent.size() < 15; i--) {
                Map<String, Object> a = parseAudit(lines.get(i));
                if (a != null) recent.add(a);
            }
            if (recent.size() >= 15) break;
        }
        List<Map<String, Object>> top = players.stream().filter(p -> num(p.get("unique")) > 0)
                .sorted(Comparator.comparingLong(p -> -num(p.get("unique")))).limit(10)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uuid", p.get("uuid"));
                    m.put("name", p.get("name"));
                    m.put("unique", p.get("unique"));
                    m.put("total", p.get("total"));
                    return m;
                }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cycle", cyc);
        out.put("playerCount", players.size());
        out.put("activeCount", players.stream().filter(p -> truthy(p.get("active"))).count());
        out.put("online", players.stream().filter(p -> truthy(p.get("online"))).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", p.get("uuid"));
            m.put("name", p.get("name"));
            return m;
        }).toList());
        out.put("top", top);
        out.put("warnings", warnings);
        out.put("effects", effects);
        out.put("recent", recent);
        return out;
    }

    List<Map<String, Object>> players() throws SQLException {
        try (Connection c = open()) {
            Map<String, Object> cyc = cycle(c);
            return players(c, cyc == null ? null : cyc.get("id"));
        }
    }

    /** Null when there is no such player. */
    Map<String, Object> player(String uuid) throws SQLException {
        try (Connection c = open()) {
            Map<String, Object> p = one(c, "SELECT * FROM players WHERE uuid=?", uuid);
            if (p == null) return null;
            Map<String, Object> cyc = cycle(c);
            Object cid = cyc == null ? -1 : cyc.get("id");
            p.remove("texture_value");
            p.remove("texture_signature");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("player", p);
            out.put("vault", rows(c, "SELECT slot, head_id, head_owner, head_owner_name, source, created_ms FROM vault_items WHERE uuid=? ORDER BY slot", uuid));
            out.put("stats", one(c, "SELECT * FROM cycle_players WHERE cycle=? AND uuid=?", cid, uuid));
            out.put("results", rows(c, "SELECT * FROM results WHERE uuid=? ORDER BY cycle DESC", uuid));
            out.put("titles", rows(c, "SELECT title FROM titles WHERE uuid=?", uuid).stream().map(r -> r.get("title")).toList());
            out.put("flags", rows(c, "SELECT flag FROM flags WHERE uuid=?", uuid).stream().map(r -> r.get("flag")).toList());
            out.put("effects", rows(c, "SELECT id, type, source, state, duration_kind, ends_ms, remaining_ms, cycles_left FROM active_actions WHERE uuid=?", uuid));
            out.put("purchases", rows(c, "SELECT entry, tier, price, ms, cycle FROM purchases WHERE uuid=? ORDER BY ms DESC", uuid));
            return out;
        }
    }

    List<Map<String, Object>> history() throws SQLException {
        try (Connection c = open()) {
            List<Map<String, Object>> cycles = rows(c, "SELECT id, start_ms, end_ms, ended_ms, state FROM cycles ORDER BY id DESC");
            for (Map<String, Object> cy : cycles) {
                List<Map<String, Object>> act = rows(c, "SELECT name, active, score, unique_heads FROM results WHERE cycle=?", cy.get("id")).stream()
                        .filter(r -> truthy(r.get("active")) && r.get("score") != null).toList();
                cy.put("active", act.size());
                double sum = act.stream().mapToDouble(r -> ((Number) r.get("score")).doubleValue()).sum();
                cy.put("avg", act.isEmpty() ? null : Math.round(sum / act.size() * 100.0) / 100.0);
                cy.put("rewarded", act.stream().filter(r -> ((Number) r.get("score")).doubleValue() > 0).count());
                cy.put("punished", act.stream().filter(r -> ((Number) r.get("score")).doubleValue() < 0).count());
                cy.put("top", String.join(", ", act.stream()
                        .sorted(Comparator.comparingDouble(r -> -((Number) r.get("score")).doubleValue())).limit(3)
                        .map(r -> r.get("name") + " (" + r.get("score") + ")").toList()));
            }
            return cycles;
        }
    }

    List<Map<String, Object>> cycleResults(long id) throws SQLException {
        try (Connection c = open()) {
            return rows(c, "SELECT * FROM results WHERE cycle=? ORDER BY rank IS NULL, rank, score DESC", id);
        }
    }

    List<Map<String, Object>> effects() throws SQLException {
        try (Connection c = open()) {
            return rows(c, "SELECT a.id, a.uuid, COALESCE(p.name, a.uuid) AS name, a.type, a.source, a.state, a.duration_kind, a.ends_ms, "
                    + "a.remaining_ms, a.cycles_left FROM active_actions a LEFT JOIN players p ON p.uuid = a.uuid ORDER BY a.ends_ms");
        }
    }

    private String uuidOf(String name) {
        try (Connection c = open()) {
            Map<String, Object> r = one(c, "SELECT uuid FROM players WHERE name_lower=?", name.toLowerCase(Locale.ROOT));
            return r == null ? null : String.valueOf(r.get("uuid"));
        } catch (SQLException | Missing e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- audit log

    static Map<String, Object> parseAudit(String line) {
        Matcher m = AUDIT_LINE.matcher(line.strip());
        if (!m.matches()) return null;
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String part : m.group(3).split(" ")) {
            int eq = part.indexOf('=');
            if (eq > 0) fields.put(part.substring(0, eq), part.substring(eq + 1));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ts", m.group(1));
        out.put("type", m.group(2));
        out.put("fields", fields);
        return out;
    }

    /** The audit files, newest first. */
    private List<File> auditFiles() {
        File[] files = auditFolder.get().listFiles((d, n) -> n.startsWith("audit-"));
        if (files == null) return List.of();
        return Arrays.stream(files).sorted(Comparator.comparingLong(File::lastModified).reversed()).toList();
    }

    private static List<String> readLines(File f) {
        try (InputStream raw = new FileInputStream(f);
             InputStream in = f.getName().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return r.lines().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    Map<String, Object> audit(String q, String type, int page) {
        int size = 40;
        List<String[]> terms = new ArrayList<>();
        for (String t : q.strip().split("\\s+")) {
            if (t.isEmpty()) continue;
            String u = t.contains("-") ? null : uuidOf(t);
            terms.add(new String[]{t.toLowerCase(Locale.ROOT), u == null ? null : u.toLowerCase(Locale.ROOT)});
        }
        Pattern typed = type.isEmpty() ? null : Pattern.compile("^\\S+\\s+" + Pattern.quote(type) + "(\\s|$)");
        int want = page * size + 1;
        List<Map<String, Object>> found = new ArrayList<>();
        outer:
        for (File f : auditFiles()) {
            List<String> lines = readLines(f);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (typed != null && !typed.matcher(line).find()) continue;
                String low = line.toLowerCase(Locale.ROOT);
                boolean all = true;
                for (String[] t : terms) {
                    if (!(low.contains(t[0]) || (t[1] != null && low.contains(t[1])))) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    Map<String, Object> a = parseAudit(line);
                    if (a != null) found.add(a);
                }
                if (found.size() >= want) break outer;
            }
        }
        int start = (page - 1) * size;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lines", start < found.size() ? found.subList(start, Math.min(found.size(), start + size)) : List.of());
        out.put("more", found.size() > start + size);
        return out;
    }

    Map<String, Object> auditHead(String id) {
        List<Map<String, Object>> found = new ArrayList<>();
        List<File> files = new ArrayList<>(auditFiles());
        java.util.Collections.reverse(files);
        for (File f : files) {
            for (String line : readLines(f)) {
                if (line.toLowerCase(Locale.ROOT).contains(id)) {
                    Map<String, Object> a = parseAudit(line);
                    if (a != null) found.add(a);
                }
            }
        }
        found.sort(Comparator.comparing(a -> String.valueOf(a.get("ts"))));
        return Map.of("lines", found);
    }
}
