package com.gradychastain.headcount.action;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.scoring.Expression;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.NumberRange;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional requirements for an action, outcome rule or shop entry:
 * {@code statuses} (all must match; {@code !name} negates), {@code any-statuses}, {@code score}, {@code streak},
 * {@code rank}, {@code points}, {@code unique} (ranges like {@code ..-1} or {@code >=3}), {@code active},
 * {@code permission}, and {@code expression} (any formula expression, true when non-zero).
 * Score, rank and streak come from the context when present (outcomes pass the cycle's final values) and from the
 * live preview otherwise.
 */
public final class Conditions {
    public static final Conditions NONE = new Conditions(Cfg.EMPTY);

    private final List<String> statuses;
    private final List<String> anyStatuses;
    private final NumberRange score, streak, rank, points, unique;
    private final Boolean active;
    private final String permission;
    private final Expression expression;
    private final boolean empty;

    public Conditions(Cfg c) {
        statuses = c.getStringList("statuses");
        anyStatuses = c.getStringList("any-statuses");
        score = c.has("score") ? NumberRange.parse(c.get("score")) : null;
        streak = c.has("streak") ? NumberRange.parse(c.get("streak")) : null;
        rank = c.has("rank") ? NumberRange.parse(c.get("rank")) : null;
        points = c.has("points") ? NumberRange.parse(c.get("points")) : null;
        unique = c.has("unique") ? NumberRange.parse(c.get("unique")) : null;
        active = c.has("active") ? c.getBool("active", true) : null;
        permission = c.getString("permission", null);
        String expr = c.getString("expression", null);
        expression = expr == null ? null : Expression.parse(expr);
        empty = statuses.isEmpty() && anyStatuses.isEmpty() && score == null && streak == null && rank == null && points == null
                && unique == null && active == null && permission == null && expression == null;
    }

    public boolean isEmpty() {
        return empty;
    }

    /** Tests the conditions for a player (null player = global context: only expression conditions can apply). */
    public boolean test(HeadcountPlugin plugin, UUID uuid, Vars vars) {
        if (empty) return true;
        Player online = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (!statuses.isEmpty()) {
            if (online == null) return false;
            for (String s : statuses) if (!plugin.statuses().has(online, s)) return false;
        }
        if (!anyStatuses.isEmpty()) {
            if (online == null) return false;
            boolean any = false;
            for (String s : anyStatuses) {
                if (plugin.statuses().has(online, s)) {
                    any = true;
                    break;
                }
            }
            if (!any) return false;
        }
        if (permission != null && (online == null || !online.hasPermission(permission))) return false;
        Map<String, Double> live = null;
        if (score != null || streak != null || rank != null || points != null || unique != null || active != null || expression != null) {
            live = new HashMap<>();
            if (online != null && plugin.cycles().current() != null) {
                live.putAll(plugin.scores().liveVariables(online));
                if (!vars.has("score")) live.put("score", (double) plugin.scores().previewScore(live, online));
            }
            for (var e : vars.asMap().entrySet()) {
                Double n = vars.number(e.getKey());
                if (n != null) live.put(e.getKey(), n);
            }
            if (uuid != null && !live.containsKey("points")) live.put("points", (double) plugin.points().balance(uuid));
        }
        if (score != null && !inRange(score, live, "score")) return false;
        if (streak != null && !inRange(streak, live, "streak")) return false;
        if (rank != null && !inRange(rank, live, "rank")) return false;
        if (points != null && !inRange(points, live, "points")) return false;
        if (unique != null && !inRange(unique, live, "unique")) return false;
        if (active != null) {
            Double a = live.get("is_active");
            if (a == null) a = live.containsKey("active_flag") ? live.get("active_flag") : null;
            if (a == null || (a != 0) != active) return false;
        }
        if (expression != null) {
            OfflinePlayer op = uuid == null ? null : Bukkit.getOfflinePlayer(uuid);
            double v = plugin.scores().evaluate(expression, live, op);
            if (v == 0 || Double.isNaN(v)) return false;
        }
        return true;
    }

    private static boolean inRange(NumberRange range, Map<String, Double> live, String key) {
        Double v = live.get(key);
        return v != null && range.test(v);
    }
}
