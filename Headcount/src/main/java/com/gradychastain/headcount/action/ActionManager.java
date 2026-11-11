package com.gradychastain.headcount.action;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.api.action.ActionType;
import com.gradychastain.headcount.api.event.ActionAppliedEvent;
import com.gradychastain.headcount.api.event.ActionRevertedEvent;
import com.gradychastain.headcount.action.types.DenyAction;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.util.Json;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Parses and runs action lists, and tracks timed actions until they end. Tracked actions survive restarts (they are
 * rebuilt from their stored settings), are applied or reverted on the next join when their target was offline, and
 * built-in types revert themselves.
 */
public final class ActionManager implements Listener {
    private final HeadcountPlugin plugin;
    private final Map<String, ActionType> types = new ConcurrentHashMap<>();
    private final Map<String, Object> namedRaw = new LinkedHashMap<>();
    private final Map<String, ActionList> named = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();
    private final List<ActiveAction> active = new ArrayList<>();
    private final Map<UUID, List<ActiveAction>> byPlayer = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private long lastPersist;

    public ActionManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- types and named lists

    public void registerType(ActionType type) {
        types.put(type.id().toLowerCase(Locale.ROOT), type);
    }

    public ActionType type(String id) {
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> typeIds() {
        return types.keySet();
    }

    public void loadNamed(Cfg file) {
        namedRaw.clear();
        named.clear();
        Cfg lists = file.getSection("lists");
        for (String name : lists.keys()) namedRaw.put(name.toLowerCase(Locale.ROOT), lists.get(name));
        for (String name : new ArrayList<>(namedRaw.keySet())) named(name);
    }

    public boolean hasNamed(String name) {
        return namedRaw.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Set<String> namedLists() {
        return namedRaw.keySet();
    }

    /** A named list from actions.yml, parsed on first use. */
    public ActionList named(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        ActionList l = named.get(n);
        if (l != null) return l;
        Object raw = namedRaw.get(n);
        if (raw == null) return null;
        if (!resolving.add(n)) {
            plugin.getLogger().warning("actions.yml: list '" + n + "' runs itself in a loop; that reference is ignored.");
            return ActionList.EMPTY;
        }
        try {
            l = parseList(raw, "actions.yml:lists." + n);
        } finally {
            resolving.remove(n);
        }
        named.put(n, l);
        return l;
    }

    // ---------------------------------------------------------------- parsing

    /** Parses a list (or a single action, or a named list reference) from config. Bad entries are logged and skipped. */
    public ActionList parseList(Object raw, String path) {
        if (raw == null) return ActionList.EMPTY;
        List<ActionSpec> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                ActionSpec s = parseOne(list.get(i), path + "[" + i + "]");
                if (s != null) out.add(s);
            }
        } else if (raw instanceof Map<?, ?> m && Cfg.of(m).has("actions") && !Cfg.of(m).has("type")) {
            return parseList(Cfg.of(m).get("actions"), path + ".actions");
        } else {
            ActionSpec s = parseOne(raw, path);
            if (s != null) out.add(s);
        }
        return new ActionList(List.copyOf(out), path);
    }

    public ActionSpec parseOne(Object raw, String path) {
        Map<String, Object> map;
        if (raw instanceof String str) {
            String s = str.trim();
            int colon = s.indexOf(':');
            String head = colon > 0 ? s.substring(0, colon).trim().toLowerCase(Locale.ROOT) : null;
            if (head != null && types.containsKey(head)) {
                map = new LinkedHashMap<>();
                map.put("type", head);
                map.put(types.get(head).primaryKey(), s.substring(colon + 1).trim());
            } else if (colon < 0 && !hasNamed(s) && types.containsKey(s.toLowerCase(Locale.ROOT))) {
                // A bare type with no settings, e.g. "- clear-effects" (a list with that name would win).
                map = new LinkedHashMap<>();
                map.put("type", s.toLowerCase(Locale.ROOT));
            } else if (colon < 0 || hasNamed(s)) {
                map = new LinkedHashMap<>();
                map.put("type", "run");
                map.put("list", s);
            } else {
                warn(path, "unknown action type '" + head + "' in \"" + s + "\"");
                return null;
            }
        } else if (raw instanceof Map<?, ?> m) {
            map = new LinkedHashMap<>(Cfg.of(m).raw());
            if (!map.containsKey("type")) {
                for (String k : new ArrayList<>(map.keySet())) {
                    String lk = k.toLowerCase(Locale.ROOT);
                    if (types.containsKey(lk)) {
                        Object v = map.remove(k);
                        map.put("type", lk);
                        if (v != null && !(v instanceof Map<?, ?>)) map.putIfAbsent(types.get(lk).primaryKey(), v);
                        else if (v instanceof Map<?, ?> inner) Cfg.of(inner).raw().forEach(map::putIfAbsent);
                        break;
                    }
                }
            }
        } else {
            warn(path, "an action must be a map or a string, not " + raw);
            return null;
        }
        Cfg cfg = Cfg.of(map, path);
        String type = cfg.getString("type", "").toLowerCase(Locale.ROOT);
        ActionType t = types.get(type);
        if (t == null) {
            warn(path, type.isEmpty()
                    ? "no 'type' and no key naming an action type (keys: " + String.join(", ", map.keySet()) + ")"
                    : "unknown action type '" + type + "'");
            return null;
        }
        try {
            CommonOptions common = CommonOptions.parse(cfg);
            ActionHandler handler = t.parse(cfg);
            if (common.repeatMs() > 0 && common.duration().instant()) warn(path, "'repeat' needs a duration; it is ignored for instant actions");
            return new ActionSpec(type, handler, common, cfg, path);
        } catch (RuntimeException e) {
            warn(path, e.getMessage());
            return null;
        }
    }

    private void warn(String path, String msg) {
        if (warned.add(path + msg)) plugin.getLogger().warning("Action at " + path + " skipped: " + msg);
    }

    // ---------------------------------------------------------------- running

    public void run(ActionList list, UUID target, Vars vars, String source) {
        if (list == null) return;
        for (ActionSpec s : list.actions()) runOne(s, target, vars, source);
    }

    public void runOne(ActionSpec spec, UUID target, Vars vars, String source) {
        if (spec.handler().needsPlayer() && target == null) {
            if (warned.add("global:" + spec.path())) {
                plugin.getLogger().warning("Action at " + spec.path() + " (" + spec.type() + ") needs a player but runs in a global hook; it is skipped there.");
            }
            return;
        }
        double chance = spec.common().chance();
        if (chance < 100 && ThreadLocalRandom.current().nextDouble(100) >= chance) return;
        Vars v = vars == null ? Vars.empty() : vars.copy();
        long delay = spec.common().delayMs();
        if (delay > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> execute(spec, target, v, source), Math.max(1, delay / 50));
        } else {
            execute(spec, target, v, source);
        }
    }

    private void execute(ActionSpec spec, UUID target, Vars vars, String source) {
        try {
            if (!spec.common().conditions().test(plugin, target, vars)) return;
            DurationSpec d = spec.common().duration();
            ActionHandler h = spec.handler();
            boolean tracked = !d.instant() && !(d.kind() == DurationSpec.Kind.PERMANENT && !h.stateful());
            boolean offline = target != null && Bukkit.getPlayer(target) == null;
            if (!tracked) {
                if (offline && h.requiresOnline()) {
                    queueInstant(spec, target, vars, source);
                    return;
                }
                ActionContextImpl ctx = new ActionContextImpl(plugin, target, vars, new LinkedHashMap<>(), source, null, d);
                if (h.apply(ctx)) {
                    saveSoon(target);
                    message(target, spec.common().startMessage(), vars, d, -1);
                    plugin.audit().log(AuditType.ACTION_APPLY, "type", spec.type(), "player", target == null ? "global" : plugin.players().name(target) + "(" + target + ")",
                            "source", source, "duration", d.describe());
                }
                return;
            }
            if (target == null) return;
            String key = spec.stackKey();
            List<ActiveAction> existing = new ArrayList<>();
            for (ActiveAction a : byPlayer.getOrDefault(target, List.of())) if (a.key.equals(key)) existing.add(a);
            if (!existing.isEmpty()) {
                switch (spec.common().stacking()) {
                    case IGNORE -> {
                        return;
                    }
                    case REFRESH -> {
                        ActiveAction a = existing.getFirst();
                        setTimes(a, d, spec.common().onlineTime(), false);
                        if (endingCycle > 0) a.cycle = endingCycle + 1;   // refreshed by a cycle end: a full next cycle
                        persist(a);
                        return;
                    }
                    case EXTEND -> {
                        ActiveAction a = existing.getFirst();
                        setTimes(a, d, spec.common().onlineTime(), true);
                        persist(a);
                        return;
                    }
                    case STACK -> { }
                }
            }
            long now = System.currentTimeMillis();
            ActiveAction a = new ActiveAction(UUID.randomUUID().toString(), target, source, key, spec, vars, new LinkedHashMap<>(),
                    offline && h.requiresOnline() ? ActiveAction.State.PENDING_APPLY : ActiveAction.State.ACTIVE, now, 0, 0, 0,
                    spec.common().repeatMs() > 0 ? now + spec.common().repeatMs() : 0, endingCycle > 0 ? endingCycle + 1 : plugin.cycles().currentId());
            setTimes(a, d, spec.common().onlineTime(), false);
            if (a.state == ActiveAction.State.ACTIVE) {
                if (!h.apply(ctx(a))) return;
                saveSoon(target);
                message(target, spec.common().startMessage(), vars, d, a.remaining());
            }
            track(a);
            persist(a);
            plugin.audit().log(AuditType.ACTION_APPLY, "id", a.id, "type", spec.type(), "player", plugin.players().name(target) + "(" + target + ")",
                    "source", source, "duration", d.describe(), "state", a.state.name().toLowerCase(Locale.ROOT));
            Bukkit.getPluginManager().callEvent(new ActionAppliedEvent(target, spec.type(), source, a.id));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Action " + spec.type() + " at " + spec.path() + " failed", e);
        }
    }

    private void setTimes(ActiveAction a, DurationSpec d, boolean online, boolean extend) {
        long now = System.currentTimeMillis();
        switch (d.kind()) {
            case TIME -> {
                if (online) a.remainingMs = extend ? a.remainingMs + d.millis() : d.millis();
                else a.endsMs = extend ? Math.max(a.endsMs, now) + d.millis() : now + d.millis();
            }
            case CYCLE -> a.cyclesLeft = extend ? a.cyclesLeft + 1 : 1;
            case CYCLES -> a.cyclesLeft = extend ? a.cyclesLeft + d.cycles() : d.cycles();
            default -> { }
        }
    }

    private ActionContextImpl ctx(ActiveAction a) {
        return new ActionContextImpl(plugin, a.player, a.vars, a.data, a.source, a.id, a.duration());
    }

    private void message(UUID target, String template, Vars vars, DurationSpec d, long remaining) {
        if (template == null || template.isBlank() || target == null) return;
        Player p = Bukkit.getPlayer(target);
        if (p == null) return;
        Vars v = vars.copy().put("duration", d.describe()).put("remaining", remaining < 0 ? d.describe() : Formats.duration(remaining));
        plugin.lang().sendTemplate(p, template, v);
    }

    private void track(ActiveAction a) {
        active.add(a);
        byPlayer.computeIfAbsent(a.player, k -> new ArrayList<>()).add(a);
    }

    private void untrack(ActiveAction a) {
        active.remove(a);
        List<ActiveAction> l = byPlayer.get(a.player);
        if (l != null) {
            l.remove(a);
            if (l.isEmpty()) byPlayer.remove(a.player);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /** Runs once a second: expiry, repeats, maintenance. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (ActiveAction a : new ArrayList<>(active)) {
            try {
                Player online = Bukkit.getPlayer(a.player);
                DurationSpec d = a.duration();
                if (a.state == ActiveAction.State.ACTIVE) {
                    if (d.kind() == DurationSpec.Kind.TIME) {
                        if (a.spec.common().onlineTime()) {
                            if (online != null) {
                                a.remainingMs -= 1000;
                                a.dirty = true;
                            }
                            if (a.remainingMs <= 0) {
                                expire(a, "time");
                                continue;
                            }
                        } else if (now >= a.endsMs) {
                            expire(a, "time");
                            continue;
                        }
                    }
                    boolean canRun = online != null || !a.spec.handler().requiresOnline();
                    if (a.spec.common().repeatMs() > 0 && a.nextRepeatMs > 0 && now >= a.nextRepeatMs) {
                        // Step from the planned time, not from now: otherwise each repeat runs up to a tick later
                        // than the last, and the final one can fall after the action has ended. After downtime,
                        // skip what was missed instead of running it in a burst.
                        long every = a.spec.common().repeatMs();
                        do a.nextRepeatMs += every; while (a.nextRepeatMs <= now);
                        a.dirty = true;
                        if (canRun) a.spec.handler().repeat(ctx(a));
                    }
                    if (online != null) a.spec.handler().maintain(ctx(a));
                } else if (a.state == ActiveAction.State.PENDING_APPLY && d.kind() == DurationSpec.Kind.TIME
                        && !a.spec.common().onlineTime() && now >= a.endsMs) {
                    // Ended before the player came back: never applied, nothing to revert.
                    untrack(a);
                    delete(a);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Tracked action " + a.spec.type() + " (" + a.id + ") failed during its tick", e);
            }
        }
        if (now - lastPersist > 30_000) {
            lastPersist = now;
            for (ActiveAction a : active) {
                if (a.dirty) {
                    a.dirty = false;
                    persist(a);
                }
            }
        }
    }

    /** Ends a tracked action now: reverts it (or marks it to revert on join when the player is offline). */
    public void expire(ActiveAction a, String reason) {
        untrack(a);
        if (a.state == ActiveAction.State.PENDING_APPLY) {
            delete(a);
            return;
        }
        boolean offline = Bukkit.getPlayer(a.player) == null;
        if (offline && a.spec.handler().requiresOnline() && a.spec.handler().revertible()) {
            a.state = ActiveAction.State.PENDING_REVERT;
            track(a);
            persist(a);
            return;
        }
        doRevert(a, reason);
    }

    /**
     * Item actions (give-item, take-item, equip, loot) move items between the inventory and Headcount's storage, so
     * the inventory is saved soon after any action applies or reverts; a crash then can't duplicate or lose them.
     */
    private void saveSoon(UUID player) {
        Player p = player == null ? null : Bukkit.getPlayer(player);
        if (p != null) plugin.players().scheduleSave(p);
    }

    private void doRevert(ActiveAction a, String reason) {
        untrack(a);
        try {
            if (a.spec.handler().revertible()) {
                a.spec.handler().revert(ctx(a));
                saveSoon(a.player);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Reverting action " + a.spec.type() + " (" + a.id + ") failed", e);
        }
        message(a.player, a.spec.common().endMessage(), a.vars, a.duration(), 0);
        delete(a);
        plugin.audit().log(AuditType.ACTION_REVERT, "id", a.id, "type", a.spec.type(), "player", plugin.players().name(a.player) + "(" + a.player + ")",
                "source", a.source, "reason", reason);
        Bukkit.getPluginManager().callEvent(new ActionRevertedEvent(a.player, a.spec.type(), a.source, a.id, reason));
    }

    public void onJoin(Player p) {
        // Instant actions that waited for this player.
        List<Object[]> pending = plugin.db().sync(c -> {
            List<Object[]> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, source, spec, vars FROM pending_actions WHERE uuid=? ORDER BY id")) {
                ps.setString(1, p.getUniqueId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)});
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pending_actions WHERE uuid=?")) {
                ps.setString(1, p.getUniqueId().toString());
                ps.executeUpdate();
            }
            return out;
        });
        for (Object[] row : pending) {
            ActionSpec spec = parseOne(Json.readMap((String) row[2]), "pending:" + row[0]);
            if (spec != null) execute(spec, p.getUniqueId(), Vars.fromStorable(Json.readMap((String) row[3])), (String) row[1]);
        }
        for (ActiveAction a : new ArrayList<>(byPlayer.getOrDefault(p.getUniqueId(), List.of()))) {
            try {
                switch (a.state) {
                    case PENDING_APPLY -> {
                        if (a.spec.handler().apply(ctx(a))) {
                            a.state = ActiveAction.State.ACTIVE;
                            persist(a);
                            saveSoon(a.player);
                            message(a.player, a.spec.common().startMessage(), a.vars, a.duration(), a.remaining());
                        } else {
                            untrack(a);
                            delete(a);
                        }
                    }
                    case PENDING_REVERT -> doRevert(a, "delayed-revert");
                    case ACTIVE -> a.spec.handler().maintain(ctx(a));
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Restoring action " + a.spec.type() + " for " + p.getName() + " failed", e);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        for (ActiveAction a : byPlayer.getOrDefault(event.getPlayer().getUniqueId(), List.of())) {
            if (a.dirty) {
                a.dirty = false;
                persist(a);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        for (ActiveAction a : new ArrayList<>(byPlayer.getOrDefault(id, List.of()))) {
            if (a.duration().kind() == DurationSpec.Kind.DEATH) expire(a, "death");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (ActiveAction a : new ArrayList<>(byPlayer.getOrDefault(p.getUniqueId(), List.of()))) {
                if (a.state == ActiveAction.State.ACTIVE) a.spec.handler().maintain(ctx(a));
            }
        }, 2L);
    }

    /**
     * Set while a cycle end is processing. Actions created then (outcomes, cycle-end hooks) belong to the next
     * cycle, so "duration: cycle" on a punishment lasts through the cycle that is starting, not zero seconds.
     */
    private int endingCycle = -1;

    public void setEndingCycle(int id) {
        endingCycle = id;
    }

    /** Ends "until the cycle ends" actions and counts down "for N cycles" actions. */
    public void onCycleEnd(int endedCycle) {
        for (ActiveAction a : new ArrayList<>(active)) {
            DurationSpec.Kind k = a.duration().kind();
            if (k != DurationSpec.Kind.CYCLE && k != DurationSpec.Kind.CYCLES) continue;
            if (a.cycle > endedCycle) continue;   // created by this cycle end, for the next cycle
            a.cyclesLeft--;
            if (a.cyclesLeft <= 0) expire(a, "cycle-end");
            else persist(a);
        }
    }

    // ---------------------------------------------------------------- queries and admin

    public List<ActiveAction> activeFor(UUID player) {
        return List.copyOf(byPlayer.getOrDefault(player, List.of()));
    }

    public boolean hasActiveType(UUID player, String type) {
        for (ActiveAction a : byPlayer.getOrDefault(player, List.of())) {
            if (a.state == ActiveAction.State.ACTIVE && a.spec.type().equals(type)) return true;
        }
        return false;
    }

    /** Active handlers of a given class for a player, with their contexts. */
    public <T extends ActionHandler> List<Map.Entry<T, ActionContextImpl>> handlers(UUID player, Class<T> type) {
        List<Map.Entry<T, ActionContextImpl>> out = new ArrayList<>();
        for (ActiveAction a : byPlayer.getOrDefault(player, List.of())) {
            if (a.state == ActiveAction.State.ACTIVE && type.isInstance(a.spec.handler())) out.add(Map.entry(type.cast(a.spec.handler()), ctx(a)));
        }
        return out;
    }

    /** Ends a specific tracked action early (consumed shields, claimed bounties). */
    public void end(String instanceId, String reason) {
        for (ActiveAction a : new ArrayList<>(active)) {
            if (a.id.equals(instanceId)) {
                expire(a, reason);
                return;
            }
        }
    }

    /** Saves a tracked action's data map after a handler changed it (charges used, etc.). */
    public void save(String instanceId) {
        for (ActiveAction a : active) if (a.id.equals(instanceId)) persist(a);
    }

    /** True when a deny action blocks this category (and detail, e.g. a command or material) for the player. */
    public boolean isDenied(Player p, String category, String detail) {
        if (p.hasPermission("headcount.bypass.deny." + category)) return false;
        for (var e : handlers(p.getUniqueId(), DenyAction.Handler.class)) {
            if (e.getKey().denies(category, detail)) {
                e.getKey().notify(plugin, p, category, e.getValue());
                return true;
            }
        }
        return false;
    }

    /** Removes an action by ID (admin). Returns false if not found. */
    public boolean remove(String idPrefix, UUID player, String actor) {
        for (ActiveAction a : new ArrayList<>(byPlayer.getOrDefault(player, List.of()))) {
            if (a.id.startsWith(idPrefix)) {
                plugin.audit().log(AuditType.ADMIN, "action", "remove-effect", "id", a.id, "type", a.spec.type(), "player", player, "actor", actor);
                expire(a, "removed-by:" + actor);
                return true;
            }
        }
        return false;
    }

    /**
     * Hard reset: ends every tracked action for everyone. One that can only be undone with its player online (a
     * potion, an attribute) waits for them as usual. @return how many were ended
     */
    public int expireAll(String reason) {
        int n = 0;
        for (ActiveAction a : new ArrayList<>(active)) {
            if (a.state == ActiveAction.State.PENDING_REVERT) continue;
            expire(a, reason);
            n++;
        }
        return n;
    }

    public int removeAll(UUID player, String actor) {
        int n = 0;
        for (ActiveAction a : new ArrayList<>(byPlayer.getOrDefault(player, List.of()))) {
            expire(a, "removed-by:" + actor);
            n++;
        }
        if (n > 0) plugin.audit().log(AuditType.ADMIN, "action", "remove-all-effects", "player", player, "count", n, "actor", actor);
        return n;
    }

    /** Text lines for /hc effects. */
    public List<Vars> describe(UUID player) {
        List<Vars> out = new ArrayList<>();
        for (ActiveAction a : byPlayer.getOrDefault(player, List.of())) {
            String desc = a.spec.handler().describe(ctx(a));
            long rem = a.remaining();
            var lang = plugin.lang();
            String left = switch (a.duration().kind()) {
                case TIME -> lang.fill(a.spec.common().onlineTime() ? "effects.remaining.online-time" : "effects.remaining.time", "time", Formats.duration(rem));
                case CYCLE -> lang.fill("effects.remaining.cycle");
                case CYCLES -> lang.fill(a.cyclesLeft == 1 ? "effects.remaining.cycles-one" : "effects.remaining.cycles", "n", a.cyclesLeft);
                case DEATH -> lang.fill("effects.remaining.death");
                case PERMANENT -> lang.fill("effects.remaining.permanent");
                case MANUAL -> lang.fill("effects.remaining.manual");
                case INSTANT -> "";
            };
            // Descriptions are MiniMessage (the built-in ones come from lang effects.describe.*).
            String description = desc == null ? plugin.text().escape(a.spec.type()) : desc;
            out.add(Vars.of("id", a.id.substring(0, 8), "type", a.spec.type(), "source", a.source)
                    .mini("description", description).mini("remaining", left)
                    .mini("state", lang.fill("effects.state." + a.state.name().toLowerCase(Locale.ROOT).replace('_', '-'))));
        }
        return out;
    }

    // ---------------------------------------------------------------- persistence

    private void queueInstant(ActionSpec spec, UUID target, Vars vars, String source) {
        String specJson = Json.write(spec.raw().raw());
        String varsJson = Json.write(vars.toStorable());
        long now = System.currentTimeMillis();
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pending_actions(uuid, source, spec, vars, created_ms) VALUES (?,?,?,?,?)")) {
                ps.setString(1, target.toString());
                ps.setString(2, source);
                ps.setString(3, specJson);
                ps.setString(4, varsJson);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
        });
    }

    private void persist(ActiveAction a) {
        String spec = Json.write(a.spec.raw().raw());
        String vars = Json.write(a.vars.toStorable());
        String data = Json.write(a.data);
        String state = a.state.name();
        String kind = a.duration().kind().name();
        String mode = a.spec.common().onlineTime() ? "online" : "real";
        long ends = a.endsMs, rem = a.remainingMs, next = a.nextRepeatMs;
        int cycles = a.cyclesLeft;
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT OR REPLACE INTO active_actions(id, uuid, source, action_key, type, spec, vars, data, state, created_ms,
                      duration_kind, time_mode, ends_ms, remaining_ms, cycles_left, next_repeat_ms, cycle)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")) {
                ps.setString(1, a.id);
                ps.setString(2, a.player.toString());
                ps.setString(3, a.source);
                ps.setString(4, a.key);
                ps.setString(5, a.spec.type());
                ps.setString(6, spec);
                ps.setString(7, vars);
                ps.setString(8, data);
                ps.setString(9, state);
                ps.setLong(10, a.createdMs);
                ps.setString(11, kind);
                ps.setString(12, mode);
                ps.setLong(13, ends);
                ps.setLong(14, rem);
                ps.setInt(15, cycles);
                ps.setLong(16, next);
                ps.setInt(17, a.cycle);
                ps.executeUpdate();
            }
        });
    }

    private void delete(ActiveAction a) {
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM active_actions WHERE id=?")) {
                ps.setString(1, a.id);
                ps.executeUpdate();
            }
        });
    }

    /** Loads tracked actions from the database (after all action types are registered). */
    public void loadActive() {
        active.clear();
        byPlayer.clear();
        List<Map<String, Object>> rows = plugin.db().sync(c -> {
            List<Map<String, Object>> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM active_actions ORDER BY created_ms"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    for (String col : List.of("id", "uuid", "source", "action_key", "type", "spec", "vars", "data", "state")) m.put(col, rs.getString(col));
                    m.put("created_ms", rs.getLong("created_ms"));
                    m.put("ends_ms", rs.getLong("ends_ms"));
                    m.put("remaining_ms", rs.getLong("remaining_ms"));
                    m.put("cycles_left", rs.getInt("cycles_left"));
                    m.put("next_repeat_ms", rs.getLong("next_repeat_ms"));
                    m.put("cycle", rs.getInt("cycle"));
                    out.add(m);
                }
            }
            return out;
        });
        int skipped = 0;
        for (Map<String, Object> m : rows) {
            ActionSpec spec = parseOne(Json.readMap((String) m.get("spec")), "tracked:" + m.get("id"));
            if (spec == null) {
                skipped++;
                continue;
            }
            ActiveAction a = new ActiveAction((String) m.get("id"), UUID.fromString((String) m.get("uuid")), (String) m.get("source"),
                    (String) m.get("action_key"), spec, Vars.fromStorable(Json.readMap((String) m.get("vars"))),
                    new LinkedHashMap<>(Json.readMap((String) m.get("data"))), ActiveAction.State.valueOf((String) m.get("state")),
                    (Long) m.get("created_ms"), (Long) m.get("ends_ms"), (Long) m.get("remaining_ms"), (Integer) m.get("cycles_left"),
                    (Long) m.get("next_repeat_ms"), (Integer) m.get("cycle"));
            track(a);
        }
        if (skipped > 0) plugin.getLogger().warning(skipped + " tracked action(s) could not be rebuilt (unknown type?); they stay stored and load once the type exists.");
    }

    /** Applies maintenance to everyone online (after a reload). */
    public void maintainOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ActiveAction a : byPlayer.getOrDefault(p.getUniqueId(), List.of())) {
                if (a.state == ActiveAction.State.ACTIVE) a.spec.handler().maintain(ctx(a));
            }
        }
    }

    /** Saves everything (shutdown). */
    public void saveAll() {
        for (ActiveAction a : active) persist(a);
    }
}
