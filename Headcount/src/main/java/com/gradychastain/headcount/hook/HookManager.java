package com.gradychastain.headcount.hook;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionList;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.cycle.CycleResults;
import com.gradychastain.headcount.cycle.PlayerResult;
import com.gradychastain.headcount.player.PlayerStore;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.util.Json;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Runs action lists at moments in the cycle and in a player's time on the server (config.yml {@code hooks}).
 * <p>
 * A cycle hook ({@code cycle-start}, {@code cycle-end}, each of {@code warnings}) has a {@code global} list that runs
 * once with cycle-wide variables and a {@code per-player} block that runs for each player with their own variables.
 * Per-player hooks for offline players are stored and delivered on their next join (unless {@code offline: skip}),
 * and dropped once more than {@code expire-after-cycles} cycles have passed.
 * <p>
 * Player hooks: {@code first-join}, {@code first-join-cycle}, {@code became-active}, {@code join}, and the ones that
 * follow something the player did ({@code hurt-pet}, {@code kill-pet}, {@code open-trapped-chest}, run by
 * {@link PetAndChestHooks}), which can have a per-player cooldown.
 */
public final class HookManager {
    private final HeadcountPlugin plugin;
    private final Map<String, ActionList> cache = new LinkedHashMap<>();
    /** Last run of a cooled-down player hook, by "uuid:hook". */
    private final Map<String, Long> lastRun = new java.util.HashMap<>();

    public HookManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        cache.clear();
    }

    private Cfg hooks() {
        return plugin.configs().main().getSection("hooks");
    }

    private ActionList list(String path, Object raw) {
        return cache.computeIfAbsent(path, p -> plugin.actions().parseList(raw, "config.yml:" + p));
    }

    // ---------------------------------------------------------------- variables

    public Vars cycleVars(Cycle cycle) {
        Vars v = Vars.empty();
        if (cycle == null) return v;
        v.put("cycle", cycle.id()).put("cycle_start", Formats.date(cycle.startMs())).put("cycle_end", Formats.date(cycle.endMs()))
                .put("time_left", Formats.duration(cycle.remainingMs())).put("cycle_length", cycle.lengthDays())
                .put("vault_size", cycle.vaultSize()).put("starting_heads", cycle.startingHeads())
                .put("active", plugin.stats().activeCount()).put("players", plugin.stats().totalPlayers());
        return v;
    }

    public Vars resultVars(CycleResults r) {
        Vars v = cycleVars(r.cycle());
        v.put("active", r.active()).put("players", r.totalPlayers()).put("average_score", String.format(Locale.ROOT, "%.1f", r.averageScore()))
                .put("rewarded", r.rewarded()).put("punished", r.punished());
        int topN = Math.max(1, hooks().getInt("top-count", 3));
        List<PlayerResult> top = r.top(Math.max(topN, 10));
        StringBuilder lines = new StringBuilder();
        String lineTemplate = plugin.lang().raw("hooks.top-line");
        for (int i = 0; i < Math.max(topN, 10); i++) {
            String n = i < top.size() ? top.get(i).name() : "-";
            int u = i < top.size() ? top.get(i).unique() : 0;
            Integer s = i < top.size() ? top.get(i).score() : null;
            v.put("top_" + (i + 1) + "_name", n).put("top_" + (i + 1) + "_unique", u).put("top_" + (i + 1) + "_score", s == null ? 0 : s);
            if (i < topN && i < top.size()) {
                if (!lines.isEmpty()) lines.append("\n");
                lines.append(lineTemplate.replace("<rank>", String.valueOf(i + 1)).replace("<name>", plugin.text().escape(n))
                        .replace("<unique>", String.valueOf(u)).replace("<score>", String.valueOf(s == null ? 0 : s)));
            }
        }
        v.mini("top_list", lines.isEmpty() ? plugin.lang().raw("hooks.top-empty") : lines.toString());
        return v;
    }

    public Vars playerResultVars(PlayerResult r, Cycle cycle) {
        Vars v = plugin.outcomes().vars(r, cycle);
        List<String> names = new ArrayList<>();
        for (String o : r.outcomes()) names.add(plugin.outcomes().display(o));
        v.mini("outcomes", names.isEmpty() ? plugin.lang().raw("hooks.no-outcomes") : String.join("<gray>, </gray>", names));
        v.put("active_flag", r.active() ? 1 : 0).put("is_active", r.active() ? 1 : 0);
        v.mini("tier", r.tier() == null || r.tier().isEmpty() ? plugin.lang().raw("hooks.no-tier") : r.tier());
        v.mini("streak_text", streakText(r.streak()));
        return v;
    }

    private String streakText(int streak) {
        if (streak > 0) return plugin.lang().raw("streak.positive").replace("<n>", String.valueOf(streak));
        if (streak < 0) return plugin.lang().raw("streak.negative").replace("<n>", String.valueOf(-streak));
        return plugin.lang().raw("streak.none");
    }

    // ---------------------------------------------------------------- cycle hooks

    public void runCycleStart(Cycle cycle) {
        runCycleHook("cycle-start", hooks().getSection("cycle-start"), cycle, cycleVars(cycle), null);
    }

    public void runCycleEnd(CycleResults results) {
        Map<UUID, Vars> perPlayer = new LinkedHashMap<>();
        for (PlayerResult r : results.players().values()) perPlayer.put(r.uuid(), playerResultVars(r, results.cycle()));
        runCycleHook("cycle-end", hooks().getSection("cycle-end"), results.cycle(), resultVars(results), perPlayer);
    }

    public void runWarning(String id, Cfg w, Cycle cycle) {
        runCycleHook("warnings." + id, w, cycle, cycleVars(cycle).put("warning", id), null);
    }

    /**
     * @param perPlayerVars variables per player (cycle end) or null to use each player's basic variables
     */
    private void runCycleHook(String id, Cfg hook, Cycle cycle, Vars global, Map<UUID, Vars> perPlayerVars) {
        if (hook.isEmpty() || !hook.getBool("enabled", true)) return;
        ActionList g = list("hooks." + id + ".global", hook.get("global"));
        plugin.actions().run(g, null, global, "hook:" + id);
        Cfg pp = hook.getSection("per-player");
        if (pp.isEmpty()) return;
        ActionList actions = list("hooks." + id + ".per-player.actions", pp.get("actions"));
        if (actions.isEmpty()) return;
        String who = pp.getString("players", "all").toLowerCase(Locale.ROOT);
        boolean deliverOffline = !pp.getString("offline", "deliver").equalsIgnoreCase("skip");
        List<UUID> targets;
        if (who.equals("online")) {
            targets = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) targets.add(p.getUniqueId());
        } else if (perPlayerVars != null) {
            targets = new ArrayList<>(perPlayerVars.keySet());
        } else {
            targets = plugin.db().sync(PlayerStore::allPlayersNow);
        }
        for (UUID uuid : targets) {
            Vars v = global.copy();
            if (perPlayerVars != null && perPlayerVars.containsKey(uuid)) v.putAll(perPlayerVars.get(uuid));
            if (who.equals("active") && !(v.number("active_flag") != null && v.number("active_flag") > 0)) continue;
            if (who.equals("inactive") && v.number("active_flag") != null && v.number("active_flag") > 0) continue;
            v.put("player", plugin.players().name(uuid));
            if (Bukkit.getPlayer(uuid) != null) {
                plugin.actions().run(actions, uuid, v, "hook:" + id);
            } else if (deliverOffline) {
                store(uuid, id, cycle == null ? 0 : cycle.id(), v);
            }
        }
    }

    private void store(UUID uuid, String hook, int cycle, Vars vars) {
        String json = Json.write(vars.toStorable());
        long now = System.currentTimeMillis();
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pending_hooks(uuid, hook, cycle, vars, created_ms) VALUES (?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, hook);
                ps.setInt(3, cycle);
                ps.setString(4, json);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
        });
    }

    /** Runs per-player cycle hooks that waited for this player (first in the join sequence). */
    public void deliverPending(Player p) {
        record Pending(String hook, int cycle, String vars) {}
        List<Pending> list = plugin.db().sync(c -> {
            List<Pending> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT hook, cycle, vars FROM pending_hooks WHERE uuid=? ORDER BY id")) {
                ps.setString(1, p.getUniqueId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Pending(rs.getString(1), rs.getInt(2), rs.getString(3)));
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pending_hooks WHERE uuid=?")) {
                ps.setString(1, p.getUniqueId().toString());
                ps.executeUpdate();
            }
            return out;
        });
        int now = plugin.cycles().currentId();
        for (Pending pending : list) {
            Cfg hook = hooks().getSection(pending.hook());
            Cfg pp = hook.getSection("per-player");
            int expire = pp.getInt("expire-after-cycles", 1);
            // A hook from cycle N is still delivered during cycle N + expire.
            if (expire >= 0 && now - pending.cycle() > expire) continue;
            ActionList actions = list("hooks." + pending.hook() + ".per-player.actions", pp.get("actions"));
            plugin.actions().run(actions, p.getUniqueId(), Vars.fromStorable(Json.readMap(pending.vars())).put("player", p.getName()), "hook:" + pending.hook());
        }
    }

    // ---------------------------------------------------------------- player hooks

    public void runPlayerHook(Player p, String id) {
        runPlayerHook(p, id, null, false);
    }

    /** Whether a player hook has any actions, so callers can skip building its variables. */
    public boolean hasPlayerHook(String id) {
        Object raw = hooks().get("player." + id);
        return raw != null && !list("hooks.player." + id, raw).isEmpty();
    }

    /**
     * Runs a player hook with extra variables. With {@code cooldown}, it is skipped if it ran for this player less
     * than {@code hooks.cooldowns.<id>} ago (hooks that can fire many times a minute, like hurt-pet).
     */
    public void runPlayerHook(Player p, String id, Vars extra, boolean cooldown) {
        Object raw = hooks().get("player." + id);
        if (raw == null) return;
        ActionList list = list("hooks.player." + id, raw);
        if (list.isEmpty()) return;
        if (cooldown) {
            long wait = cooldown(id);
            String key = p.getUniqueId() + ":" + id;
            long now = System.currentTimeMillis();
            Long last = lastRun.get(key);
            if (wait > 0 && last != null && now - last < wait) return;
            lastRun.put(key, now);
        }
        Vars v = cycleVars(plugin.cycles().current()).put("player", p.getName());
        var d = plugin.players().get(p);
        v.put("points", d.points).put("streak", d.streak).mini("streak_text", streakText(d.streak));
        if (extra != null) v.putAll(extra);
        plugin.actions().run(list, p.getUniqueId(), v, "hook:player." + id);
    }

    /** hooks.cooldowns.&lt;id&gt;; hooks is replaced as a whole by the owner's file, so a missing entry uses the bundled one. */
    private long cooldown(String id) {
        String key = "cooldowns." + id;
        if (hooks().has(key)) return hooks().getMillis(key, 0);
        return plugin.configs().defaults("config.yml").getSection("hooks").getMillis(key, 0);
    }

    /** Forgets a player's hook cooldowns when they leave. */
    public void forget(java.util.UUID uuid) {
        String prefix = uuid + ":";
        lastRun.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Admin test: run a hook now. Cycle hooks run for real (global + per-player); player hooks on one player. */
    public boolean runByName(String id, Player target) {
        String n = id.toLowerCase(Locale.ROOT);
        Cycle cycle = plugin.cycles().current();
        switch (n) {
            case "cycle-start" -> {
                if (cycle == null) return false;
                runCycleStart(cycle);
                return true;
            }
            case "first-join", "first-join-cycle", "became-active", "join" -> {
                if (target == null) return false;
                runPlayerHook(target, n);
                return true;
            }
            case PetAndChestHooks.HURT_PET, PetAndChestHooks.KILL_PET, PetAndChestHooks.OPEN_TRAPPED_CHEST -> {
                // A test run on the player as if they did it where they stand, to a wolf the player owns, without the cooldown.
                if (target == null) return false;
                Vars v = PetAndChestHooks.locationVars(target.getLocation());
                if (!n.equals(PetAndChestHooks.OPEN_TRAPPED_CHEST)) {
                    v.put("owner", target.getName()).put("owner_uuid", target.getUniqueId().toString())
                            .put("pet", net.kyori.adventure.text.Component.translatable(org.bukkit.entity.EntityType.WOLF.translationKey()))
                            .put("pet_type", PetAndChestHooks.petType(org.bukkit.entity.EntityType.WOLF));
                    if (n.equals(PetAndChestHooks.HURT_PET)) v.put("damage", 2);
                }
                runPlayerHook(target, n, v, false);
                return true;
            }
            default -> {
                if (n.startsWith("warning:") || n.startsWith("warnings.")) {
                    String w = n.substring(n.indexOf(n.startsWith("warning:") ? ':' : '.') + 1);
                    Cfg wc = hooks().getSection("warnings." + w);
                    if (wc.isEmpty() || cycle == null) return false;
                    runWarning(w, wc, cycle);
                    return true;
                }
                return false;
            }
        }
    }
}
