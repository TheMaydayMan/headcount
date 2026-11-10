package com.gradychastain.headcount.outcome;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionList;
import com.gradychastain.headcount.action.Conditions;
import com.gradychastain.headcount.action.types.HeadcountActions;
import com.gradychastain.headcount.api.event.OutcomeApplyEvent;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.cycle.PlayerResult;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.util.NumberRange;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * End-of-cycle rules from outcomes.yml. Each rule has a score range, optional conditions and an action list.
 * In {@code most-extreme} mode a player gets only the most extreme matching rule of each group; in {@code all} mode,
 * every matching rule. Rules marked {@code punishment} can be blocked by a shield.
 */
public final class OutcomeManager {
    public record Rule(String id, String display, NumberRange score, Conditions conditions, ActionList actions, boolean punishment,
                       String group, Double priority, int order) {
        double extremeness() {
            return priority != null ? priority : score.extremeness();
        }
    }

    private final HeadcountPlugin plugin;
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private boolean mostExtreme = true;

    public OutcomeManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(Cfg file) {
        rules.clear();
        mostExtreme = !file.getString("mode", "most-extreme").equalsIgnoreCase("all");
        Cfg section = file.getSection("rules");
        int order = 0;
        for (String id : section.keys()) {
            Cfg r = section.getSection(id);
            if (!r.getBool("enabled", true)) continue;
            try {
                NumberRange score = NumberRange.parse(r.get("score"));
                boolean punishment = r.has("punishment") ? r.getBool("punishment", false) : score.max() < 0;
                Conditions cond = r.isSection("conditions") ? new Conditions(r.getSection("conditions")) : Conditions.NONE;
                ActionList actions = plugin.actions().parseList(r.get("actions"), "outcomes.yml:rules." + id + ".actions");
                Double priority = r.has("priority") ? r.getDouble("priority", 0) : null;
                rules.put(id, new Rule(id, r.getString("display", id), score, cond, actions, punishment,
                        r.getString("group", punishment ? "punishments" : "rewards").toLowerCase(Locale.ROOT), priority, order++));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("outcomes.yml: rule '" + id + "' skipped: " + e.getMessage());
            }
        }
    }

    public Map<String, Rule> rules() {
        return rules;
    }

    public Vars vars(PlayerResult r, Cycle cycle) {
        return Vars.of("player", r.name(), "score", r.score() == null ? 0 : r.score(), "unique", r.unique(), "total", r.total(),
                "own", r.own(), "rank", r.rank() == null ? 0 : r.rank(), "percentile", r.percentile() == null ? 0 : Math.round(r.percentile()),
                "streak", r.streak(), "points_earned", r.pointsEarned(), "points", r.pointsAfter(), "rolled_over", r.rolledOver(),
                "cycle", cycle.id(), "tier", r.tier() == null ? "" : r.tier(), "playtime", Formats.duration(r.playtimeMs()),
                "kills", r.kills(), "deaths", r.deaths(), "trades", r.trades());
    }

    /** The rules a result gets, after the all / most-extreme selection. */
    public List<Rule> select(PlayerResult r, Cycle cycle) {
        if (r.score() == null) return List.of();
        Vars vars = vars(r, cycle);
        List<Rule> matched = new ArrayList<>();
        for (Rule rule : rules.values()) {
            if (!rule.score().test(r.score())) continue;
            if (!rule.conditions().test(plugin, r.uuid(), vars)) continue;
            matched.add(rule);
        }
        if (!mostExtreme) return matched;
        Map<String, Rule> best = new LinkedHashMap<>();
        for (Rule rule : matched) {
            Rule cur = best.get(rule.group());
            if (cur == null || rule.extremeness() > cur.extremeness()) best.put(rule.group(), rule);
        }
        List<Rule> out = new ArrayList<>(best.values());
        out.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return out;
    }

    public boolean wouldPunish(PlayerResult r) {
        Cycle c = plugin.cycles().current();
        if (c == null) return false;
        return select(r, c).stream().anyMatch(Rule::punishment);
    }

    public boolean anyPunishment(List<String> applied) {
        for (String id : applied) {
            Rule r = rules.get(id);
            if (r != null && r.punishment()) return true;
        }
        return false;
    }

    public String display(String id) {
        if (id.startsWith("shielded:")) {
            Rule r = rules.get(id.substring(9));
            return plugin.lang().raw("outcomes.shielded-label").replace("<outcome>", r == null ? id.substring(9) : r.display());
        }
        Rule r = rules.get(id);
        return r == null ? id : r.display();
    }

    /** Applies the selected rules. Returns the IDs applied ("shielded:<id>" for punishments a shield blocked). */
    public List<String> apply(PlayerResult r, Cycle cycle) {
        List<String> applied = new ArrayList<>();
        Vars vars = vars(r, cycle);
        for (Rule rule : select(r, cycle)) {
            if (rule.punishment()) {
                var shields = plugin.actions().handlers(r.uuid(), HeadcountActions.Shield.class);
                if (!shields.isEmpty()) {
                    var s = shields.getFirst();
                    s.getKey().use(plugin, s.getValue());
                    applied.add("shielded:" + rule.id());
                    plugin.audit().log(AuditType.OUTCOME, "cycle", cycle.id(), "player", r.name() + "(" + r.uuid() + ")", "rule", rule.id(), "result", "blocked-by-shield");
                    Player p = Bukkit.getPlayer(r.uuid());
                    if (p != null) plugin.lang().send(p, "outcomes.shield-used", Vars.of("outcome", rule.display()));
                    continue;
                }
            }
            OutcomeApplyEvent event = new OutcomeApplyEvent(r.uuid(), cycle.id(), rule.id(), rule.punishment(), r.score());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            plugin.actions().run(rule.actions(), r.uuid(), vars.copy().mini("outcome", rule.display()), "outcome:" + rule.id());
            applied.add(rule.id());
            plugin.audit().log(AuditType.OUTCOME, "cycle", cycle.id(), "player", r.name() + "(" + r.uuid() + ")", "rule", rule.id(),
                    "score", r.score(), "punishment", rule.punishment());
        }
        return applied;
    }
}
