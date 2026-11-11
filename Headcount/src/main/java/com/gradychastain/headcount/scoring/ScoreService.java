package com.gradychastain.headcount.scoring;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.vault.VaultTally;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Evaluates the score formula from scoring.yml. */
public final class ScoreService {
    /** Every built-in formula variable with a one-line description (also used by docs and /hca debug). */
    public static final Map<String, String> VARIABLES = new LinkedHashMap<>();

    static {
        VARIABLES.put("unique", "unique heads in the vault (different owners; your own head counts once)");
        VARIABLES.put("total", "total heads in the vault, duplicates included");
        VARIABLES.put("own", "copies of your own head in the vault");
        VARIABLES.put("duplicates", "total - unique");
        VARIABLES.put("vault_size", "vault slots this cycle");
        VARIABLES.put("vault_free", "empty vault slots");
        VARIABLES.put("starting_heads", "heads every vault started this cycle with");
        VARIABLES.put("active", "active players this cycle");
        VARIABLES.put("players", "players who have ever joined");
        VARIABLES.put("online", "players online now");
        VARIABLES.put("rank", "your rank among active players by unique heads (1 = most)");
        VARIABLES.put("percentile", "your percentile among active players by unique heads (100 = top)");
        VARIABLES.put("unique_avg", "average unique heads of active players");
        VARIABLES.put("unique_median", "median unique heads of active players");
        VARIABLES.put("unique_max", "most unique heads any active player has");
        VARIABLES.put("unique_min", "fewest unique heads any active player has");
        VARIABLES.put("unique_sum", "sum of unique heads over active players");
        VARIABLES.put("kills", "kill heads you got this cycle");
        VARIABLES.put("deaths", "kill heads others got from you this cycle");
        VARIABLES.put("trades", "completed trades this cycle");
        VARIABLES.put("heads_in", "heads you received through trades this cycle");
        VARIABLES.put("heads_out", "heads you gave away through trades this cycle");
        VARIABLES.put("purchases", "shop purchases this cycle");
        VARIABLES.put("points_spent", "points spent this cycle");
        VARIABLES.put("points", "points you have now");
        VARIABLES.put("points_next", "points already waiting for next cycle");
        VARIABLES.put("playtime", "minutes played this cycle");
        VARIABLES.put("playtime_hours", "hours played this cycle");
        VARIABLES.put("streak", "current streak: positive for consecutive positive cycles, negative for negative ones");
        VARIABLES.put("streak_positive", "length of a positive streak, else 0");
        VARIABLES.put("streak_negative", "length of a negative streak, else 0");
        VARIABLES.put("last_score", "last cycle's score (0 if none)");
        VARIABLES.put("last_unique", "last cycle's unique heads (0 if none)");
        VARIABLES.put("has_last", "1 if you have a last-cycle score");
        VARIABLES.put("bonus", "score bonus from actions waiting for this cycle end");
        VARIABLES.put("titles", "number of titles you own");
        VARIABLES.put("cycle", "the cycle ID");
        VARIABLES.put("cycle_length", "cycle length in days");
        VARIABLES.put("days_elapsed", "days since the cycle started");
        VARIABLES.put("days_left", "days until the cycle ends");
        VARIABLES.put("is_active", "1 if you are active this cycle");
    }

    private final HeadcountPlugin plugin;
    private Expression formula;
    private String rounding = "floor";
    private boolean addBonus = true;
    private boolean denseRanks = false;
    private final Set<String> warned = new HashSet<>();

    public ScoreService(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(Cfg scoring) {
        String src = scoring.getString("formula", "unique - ceil(active / 4)");
        try {
            formula = Expression.parse(src);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("scoring.yml formula is invalid (" + e.getMessage() + "). Using the default, 'unique - ceil(active / 4)'.");
            formula = Expression.parse("unique - ceil(active / 4)");
        }
        for (String v : formula.variables()) {
            if (!VARIABLES.containsKey(v)) plugin.getLogger().warning("scoring.yml formula uses unknown variable '" + v + "', which counts as 0.");
        }
        rounding = scoring.getString("rounding", "floor").toLowerCase(Locale.ROOT);
        if (!Set.of("floor", "round", "ceil").contains(rounding)) {
            plugin.getLogger().warning("scoring.yml rounding '" + rounding + "' isn't floor, round or ceil. Using floor.");
            rounding = "floor";
        }
        addBonus = scoring.getBool("add-bonus-after-formula", true);
        denseRanks = scoring.getString("rank-mode", "competition").equalsIgnoreCase("dense");
        warned.clear();
    }

    public Expression formula() {
        return formula;
    }

    public boolean denseRanks() {
        return denseRanks;
    }

    public Map<String, Double> variables(ScoreInputs in, Population pop, Cycle cycle) {
        Map<String, Double> v = new LinkedHashMap<>();
        v.put("unique", (double) in.unique());
        v.put("total", (double) in.total());
        v.put("own", (double) in.own());
        v.put("duplicates", (double) Math.max(0, in.total() - in.unique()));
        v.put("vault_size", (double) in.vaultSize());
        v.put("vault_free", (double) Math.max(0, in.vaultSize() - in.total()));
        v.put("starting_heads", cycle == null ? 0.0 : cycle.startingHeads());
        v.put("active", (double) pop.active());
        v.put("players", (double) pop.totalPlayers());
        v.put("online", (double) Bukkit.getOnlinePlayers().size());
        v.put("rank", (double) pop.rank(in.unique()));
        v.put("percentile", pop.percentile(in.unique()));
        v.put("unique_avg", pop.average());
        v.put("unique_median", pop.median());
        v.put("unique_max", (double) pop.max());
        v.put("unique_min", (double) pop.min());
        v.put("unique_sum", (double) pop.sum());
        v.put("kills", (double) in.kills());
        v.put("deaths", (double) in.deaths());
        v.put("trades", (double) in.trades());
        v.put("heads_in", (double) in.headsIn());
        v.put("heads_out", (double) in.headsOut());
        v.put("purchases", (double) in.purchases());
        v.put("points_spent", (double) in.pointsSpent());
        v.put("points", (double) in.points());
        v.put("points_next", (double) in.pointsNext());
        v.put("playtime", in.playtimeMs() / 60000.0);
        v.put("playtime_hours", in.playtimeMs() / 3600000.0);
        v.put("streak", (double) in.streak());
        v.put("streak_positive", (double) Math.max(0, in.streak()));
        v.put("streak_negative", (double) Math.max(0, -in.streak()));
        v.put("last_score", in.lastScore() == null ? 0.0 : in.lastScore());
        v.put("last_unique", in.lastUnique() == null ? 0.0 : in.lastUnique());
        v.put("has_last", in.lastScore() == null ? 0.0 : 1.0);
        v.put("bonus", (double) in.bonus());
        v.put("titles", (double) in.titles());
        v.put("cycle", cycle == null ? 0.0 : cycle.id());
        v.put("cycle_length", cycle == null ? 0.0 : cycle.lengthDays());
        long now = System.currentTimeMillis();
        v.put("days_elapsed", cycle == null ? 0.0 : Math.max(0, now - cycle.startMs()) / 86400000.0);
        v.put("days_left", cycle == null ? 0.0 : Math.max(0, cycle.endMs() - now) / 86400000.0);
        v.put("is_active", in.active() ? 1.0 : 0.0);
        return v;
    }

    /** Evaluates an expression with the given variables, resolving %placeholders% for the player. */
    public double evaluate(Expression expr, Map<String, Double> vars, OfflinePlayer player) {
        return expr.evaluate(name -> {
            if (name.startsWith("%")) return placeholder(name, player);
            Double v = vars.get(name);
            if (v == null && warned.add(name)) plugin.getLogger().warning("Unknown formula variable '" + name + "', which counts as 0.");
            return v;
        });
    }

    private Double placeholder(String ph, OfflinePlayer player) {
        // No player (the start-up self-check tries the formula on sample values): nothing to resolve, and not a
        // reason to warn, which would also use up the one warning a real failure later should get.
        if (player == null) return 0.0;
        String out = plugin.text().resolveForNumber(ph, player);
        if (out.equals(ph)) {
            if (warned.add(ph)) plugin.getLogger().warning("Placeholder " + ph + " in a formula didn't resolve, so it counts as 0. Check that PlaceholderAPI and its expansion are installed.");
            return 0.0;
        }
        try {
            return Double.parseDouble(out.replace(",", "").trim());
        } catch (NumberFormatException e) {
            if (warned.add(ph + "#nan")) plugin.getLogger().warning("Placeholder " + ph + " returned '" + out + "', which isn't a number, so it counts as 0.");
            return 0.0;
        }
    }

    /** Applies the formula and rounding, then the bonus. */
    public int score(Map<String, Double> vars, OfflinePlayer player) {
        double raw = evaluate(formula, vars, player);
        if (Double.isNaN(raw)) {
            plugin.getLogger().warning("The score formula produced NaN for " + (player == null ? "?" : player.getName()) + ", so it counts as 0.");
            raw = 0;
        }
        double r = switch (rounding) {
            case "round" -> Math.round(raw);
            case "ceil" -> Math.ceil(raw);
            default -> Math.floor(raw);
        };
        long clamped = (long) Math.max(Integer.MIN_VALUE / 2.0, Math.min(Integer.MAX_VALUE / 2.0, r));
        if (addBonus) clamped += vars.getOrDefault("bonus", 0.0).longValue();
        return (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, clamped));
    }

    // ---------------------------------------------------------------- live preview

    /** Inputs for an online player right now. */
    public ScoreInputs liveInputs(Player p) {
        PlayerData d = plugin.players().get(p);
        VaultTally t = plugin.vaults().tally(p.getUniqueId());
        var s = d.stats;
        return new ScoreInputs(p.getUniqueId(), p.getName(), s != null && s.active, t.unique(), t.total(), t.own(),
                s == null ? 0 : s.kills, s == null ? 0 : s.deaths, s == null ? 0 : s.trades, s == null ? 0 : s.headsIn,
                s == null ? 0 : s.headsOut, s == null ? 0 : s.purchases, s == null ? 0 : s.pointsSpent,
                s == null ? 0 : s.playtimeMs + Math.max(0, System.currentTimeMillis() - d.sessionMark),
                d.streak, d.lastScore, d.lastUnique, d.points, d.pointsNext, d.scoreBonus, d.vaultSize, d.titles.size());
    }

    /** The live population, with this player counted as active (a preview asks "if the cycle ended now"). */
    public Population livePopulation(UUID self, int selfUnique) {
        List<Integer> uniques = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : plugin.stats().activeUniques().entrySet()) {
            if (!e.getKey().equals(self)) uniques.add(e.getValue());
        }
        uniques.add(selfUnique);
        return new Population(uniques, plugin.stats().totalPlayers(), denseRanks);
    }

    public Map<String, Double> liveVariables(Player p) {
        ScoreInputs in = liveInputs(p);
        return variables(in, livePopulation(p.getUniqueId(), in.unique()), plugin.cycles().current());
    }

    /** What the player's score would be if the cycle ended now. */
    public int preview(Player p) {
        if (plugin.cycles().current() == null) return 0;
        return previewScore(liveVariables(p), p);
    }

    /** The formula on live variables, passed through ScoreCalculatedEvent as a preview so addons' changes show. */
    public int previewScore(Map<String, Double> vars, Player p) {
        int s = score(vars, p);
        var ev = new com.gradychastain.headcount.api.event.ScoreCalculatedEvent(p.getUniqueId(), plugin.cycles().currentId(), s, Map.copyOf(vars), true);
        org.bukkit.Bukkit.getPluginManager().callEvent(ev);
        return ev.getScore();
    }

    /** One live variable for statuses and conditions ({@code score} gives the preview). */
    public Double variable(Player p, String name) {
        if (plugin.cycles().current() == null) return null;
        Map<String, Double> vars = liveVariables(p);
        if (name.equals("score")) return (double) previewScore(vars, p);
        return vars.get(name);
    }
}
