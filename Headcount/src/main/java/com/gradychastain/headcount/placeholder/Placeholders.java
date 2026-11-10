package com.gradychastain.headcount.placeholder;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.types.HeadcountActions;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.scoring.LiveStats;
import com.gradychastain.headcount.shop.ShopManager;
import com.gradychastain.headcount.text.PlaceholderValue;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.vault.VaultTally;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headcount's placeholders, used as {@code %headcount_<name>%} in its own text (no PlaceholderAPI needed), in the
 * score formula, and by other plugins through the PlaceholderAPI expansion.
 */
public final class Placeholders {
    /** Every placeholder with a description, for docs and /hca debug placeholders. */
    public static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put("unique", "unique heads in your vault");
        ALL.put("total", "total heads in your vault");
        ALL.put("own", "copies of your own head in your vault");
        ALL.put("duplicates", "total minus unique");
        ALL.put("score", "your score if the cycle ended now");
        ALL.put("points", "points to spend");
        ALL.put("points_next", "points waiting for next cycle");
        ALL.put("streak", "streak as a signed number (+3, -2, 0)");
        ALL.put("streak_text", "streak as text from the language file");
        ALL.put("last_score", "last cycle's score");
        ALL.put("last_unique", "last cycle's unique heads");
        ALL.put("rank", "your live rank by unique heads");
        ALL.put("percentile", "your live percentile");
        ALL.put("active", "whether you are active this cycle");
        ALL.put("playtime", "playtime this cycle");
        ALL.put("kills", "kill heads you got this cycle");
        ALL.put("deaths", "kill heads others got from you this cycle");
        ALL.put("trades", "completed trades this cycle");
        ALL.put("vault_size", "your vault size");
        ALL.put("vault_used", "filled vault slots");
        ALL.put("vault_free", "empty vault slots");
        ALL.put("starting_heads", "heads each vault started with this cycle");
        ALL.put("cycle", "the current cycle ID");
        ALL.put("cycle_start", "when the cycle started");
        ALL.put("cycle_end", "when the cycle ends");
        ALL.put("time_left", "time left in the cycle, e.g. 3d 4h");
        ALL.put("time_left_clock", "time left as HH:MM:SS");
        ALL.put("seconds_left", "seconds left in the cycle");
        ALL.put("cycle_length", "cycle length in days");
        ALL.put("active_players", "active players this cycle");
        ALL.put("total_players", "players who have ever joined");
        ALL.put("title", "equipped title (formatted)");
        ALL.put("title_raw", "equipped title as plain text");
        ALL.put("title_id", "equipped title ID");
        ALL.put("tier", "highest shop tier you have unlocked");
        ALL.put("tier_id", "its ID");
        ALL.put("shield", "shield charges available");
        ALL.put("bounty", "points on your head");
        ALL.put("effects", "number of active effects");
        ALL.put("sidebar", "whether your sidebar is on");
        ALL.put("in_trade", "whether you are trading");
        ALL.put("trade_cooldown", "time until you can trade again");
        ALL.put("bedrock", "whether you play on Bedrock");
        ALL.put("status_<name>", "whether you have a status from statuses.yml");
        ALL.put("flag_<name>", "whether a Headcount flag is set");
        ALL.put("top_<n>_name", "name of the player ranked n by unique heads");
        ALL.put("top_<n>_unique", "their unique heads");
        ALL.put("top_<n>_total", "their total heads");
    }

    private final HeadcountPlugin plugin;

    public Placeholders(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private PlaceholderValue bool(boolean b) {
        return PlaceholderValue.plain(b ? plugin.configs().main().getString("placeholders.true-text", "yes")
                : plugin.configs().main().getString("placeholders.false-text", "no"));
    }

    private static PlaceholderValue plain(Object o) {
        return PlaceholderValue.plain(o);
    }

    public PlaceholderValue resolve(OfflinePlayer player, String rawParams) {
        String params = rawParams.toLowerCase(Locale.ROOT);
        Cycle cycle = plugin.cycles().current();
        // Server-wide values first: they work without a player.
        switch (params) {
            case "cycle": return plain(cycle == null ? 0 : cycle.id());
            case "cycle_start": return plain(cycle == null ? "-" : Formats.date(cycle.startMs()));
            case "cycle_end": return plain(cycle == null ? "-" : Formats.date(cycle.endMs()));
            case "time_left": return plain(cycle == null ? plugin.lang().raw("placeholders.not-started") : Formats.duration(cycle.remainingMs()));
            case "time_left_clock": return plain(cycle == null ? "--:--" : Formats.clock(cycle.remainingMs()));
            case "seconds_left": return plain(cycle == null ? 0 : cycle.remainingMs() / 1000);
            case "cycle_length": return plain(cycle == null ? plugin.cycles().schedule().lengthDays() : cycle.lengthDays());
            case "starting_heads": return plain(cycle == null ? 0 : cycle.startingHeads());
            case "active_players": return plain(plugin.stats().activeCount());
            case "total_players": return plain(plugin.stats().totalPlayers());
            default: break;
        }
        if (params.startsWith("top_")) {
            String[] parts = params.split("_", 3);
            if (parts.length == 3) {
                try {
                    int n = Integer.parseInt(parts[1]);
                    List<LiveStats.Entry> top = plugin.stats().top();
                    LiveStats.Entry e = n >= 1 && n <= top.size() ? top.get(n - 1) : null;
                    return switch (parts[2]) {
                        case "name" -> plain(e == null ? plugin.lang().raw("placeholders.top-empty") : e.name());
                        case "unique" -> plain(e == null ? 0 : e.unique());
                        case "total" -> plain(e == null ? 0 : e.total());
                        case "uuid" -> plain(e == null ? "" : e.uuid());
                        default -> null;
                    };
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        if (player == null) return null;
        Player online = player.getPlayer();
        PlayerData d = plugin.players().loaded(player.getUniqueId());
        // Status placeholders read as statuses.yml settings.placeholder-true / placeholder-false.
        if (params.startsWith("status_")) return plain(plugin.statuses().format(online != null && plugin.statuses().has(online, params.substring(7))));
        if (params.startsWith("flag_")) return bool(plugin.players().hasFlag(player.getUniqueId(), params.substring(5)));
        switch (params) {
            case "unique", "total", "own", "duplicates", "vault_used", "vault_free" -> {
                VaultTally t = cycle == null ? VaultTally.EMPTY : plugin.vaults().tally(player.getUniqueId());
                int size = d == null ? (cycle == null ? 0 : cycle.vaultSize()) : d.vaultSize;
                return plain(switch (params) {
                    case "unique" -> t.unique();
                    case "total", "vault_used" -> t.total();
                    case "own" -> t.own();
                    case "duplicates" -> Math.max(0, t.total() - t.unique());
                    default -> Math.max(0, size - t.total());
                });
            }
            case "score" -> {
                return plain(online == null || cycle == null ? 0 : plugin.scores().preview(online));
            }
            case "points" -> {
                return plain(d != null ? d.points : plugin.points().balance(player.getUniqueId()));
            }
            case "points_next" -> {
                return plain(d != null ? d.pointsNext : plugin.points().pending(player.getUniqueId()));
            }
            case "streak" -> {
                int s = d == null ? 0 : d.streak;
                return plain(s > 0 ? "+" + s : String.valueOf(s));
            }
            case "streak_text" -> {
                int s = d == null ? 0 : d.streak;
                String key = s > 0 ? "streak.positive" : s < 0 ? "streak.negative" : "streak.none";
                return PlaceholderValue.mini(plugin.lang().raw(key).replace("<n>", String.valueOf(Math.abs(s))));
            }
            case "last_score" -> {
                return plain(d == null || d.lastScore == null ? "-" : d.lastScore);
            }
            case "last_unique" -> {
                return plain(d == null || d.lastUnique == null ? "-" : d.lastUnique);
            }
            case "rank", "percentile" -> {
                if (online == null || cycle == null) return plain("-");
                Map<String, Double> vars = plugin.scores().liveVariables(online);
                return plain(params.equals("rank") ? Formats.number(vars.getOrDefault("rank", 0.0)) : Formats.number(Math.round(vars.getOrDefault("percentile", 0.0))));
            }
            case "active" -> {
                return bool(d != null && d.stats != null && d.stats.active);
            }
            case "playtime" -> {
                long ms = d == null || d.stats == null ? 0 : d.stats.playtimeMs + Math.max(0, System.currentTimeMillis() - d.sessionMark);
                return plain(Formats.duration(ms));
            }
            case "kills" -> {
                return plain(d == null || d.stats == null ? 0 : d.stats.kills);
            }
            case "deaths" -> {
                return plain(d == null || d.stats == null ? 0 : d.stats.deaths);
            }
            case "trades" -> {
                return plain(d == null || d.stats == null ? 0 : d.stats.trades);
            }
            case "vault_size" -> {
                return plain(d == null ? (cycle == null ? 0 : cycle.vaultSize()) : d.vaultSize);
            }
            case "title" -> {
                return PlaceholderValue.mini(plugin.titles().displayFor(player.getUniqueId()));
            }
            case "title_raw" -> {
                return plain(plugin.text().mini().stripTags(plugin.titles().displayFor(player.getUniqueId())));
            }
            case "title_id" -> {
                String t = plugin.titles().equipped(player.getUniqueId());
                return plain(t == null ? "" : t);
            }
            case "tier", "tier_id" -> {
                if (online == null) return plain("");
                ShopManager.Tier t = plugin.shop().highestUnlocked(online);
                if (t == null) return plain("");
                return params.equals("tier") ? PlaceholderValue.mini(t.name()) : plain(t.id());
            }
            case "shield" -> {
                int charges = 0;
                for (var e : plugin.actions().handlers(player.getUniqueId(), HeadcountActions.Shield.class)) {
                    int used = e.getValue().data().get("used") instanceof Number n ? n.intValue() : 0;
                    int total = e.getKey().getCharges();
                    charges += total <= 0 ? 1 : Math.max(0, total - used);
                }
                return plain(charges);
            }
            case "bounty" -> {
                long pts = 0;
                for (var e : plugin.actions().handlers(player.getUniqueId(), HeadcountActions.Bounty.class)) pts += e.getKey().points;
                return plain(pts);
            }
            case "effects" -> {
                return plain(plugin.actions().activeFor(player.getUniqueId()).size());
            }
            case "sidebar" -> {
                return bool(online != null && plugin.sidebar().isShown(online));
            }
            case "in_trade" -> {
                return bool(plugin.trades().inTrade(player.getUniqueId()));
            }
            case "trade_cooldown" -> {
                return plain(Formats.duration(plugin.trades().cooldownLeft(player.getUniqueId())));
            }
            case "bedrock" -> {
                return bool(plugin.integrations().isBedrock(player.getUniqueId()));
            }
            default -> {
                return null;
            }
        }
    }

    /** Lists all placeholders with their current values for a player (debug). */
    public Map<String, String> dump(Player p) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : ALL.keySet()) {
            if (k.contains("<")) continue;
            PlaceholderValue v = resolve(p, k);
            out.put(k, v == null ? "?" : v.text());
        }
        for (String s : plugin.statuses().names()) out.put("status_" + s, resolve(p, "status_" + s).text());
        return out;
    }
}
