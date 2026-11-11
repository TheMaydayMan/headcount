package com.gradychastain.headcount.status;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.status.StatusCheck;
import com.gradychastain.headcount.api.status.StatusSourceType;
import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named player statuses from statuses.yml. Other configs refer to them by name; {@code !name} negates.
 * Results are cached for the current server tick.
 */
public final class StatusManager {
    /** A parsed status. {@code fallback} is used when the source returns null (unavailable). */
    public record Definition(String name, String source, StatusCheck check, String fallback, boolean unavailable, String description) {}

    private final HeadcountPlugin plugin;
    private final Map<String, StatusSourceType> types = new ConcurrentHashMap<>();
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    /** Statuses each status depends on (combinations and fallbacks), for loop detection at load. */
    private final Map<String, List<String>> refs = new HashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Boolean>> cache = new HashMap<>();
    private int cacheTick = -1;
    private final ThreadLocal<Deque<String>> evaluating = ThreadLocal.withInitial(ArrayDeque::new);
    private final Set<String> warnedUnknown = new HashSet<>();
    private String trueText = "true";
    private String falseText = "false";

    public StatusManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
        BuiltinStatusSources.registerAll(this, plugin);
    }

    public void registerType(StatusSourceType type) {
        types.put(type.id().toLowerCase(Locale.ROOT), type);
    }

    public Collection<StatusSourceType> types() {
        return types.values();
    }

    public void load(Cfg file) {
        definitions.clear();
        refs.clear();
        errors.clear();
        warnedUnknown.clear();
        cache.clear();
        Cfg settings = file.getSection("settings");
        trueText = settings.getString("placeholder-true", "true");
        falseText = settings.getString("placeholder-false", "false");
        Cfg statuses = file.getSection("statuses");
        for (String name : statuses.keys()) {
            Cfg s = statuses.getSection(name);
            String key = name.toLowerCase(Locale.ROOT);
            String source = s.getString("source", "").toLowerCase(Locale.ROOT);
            List<String> deps = new ArrayList<>();
            if (source.equals("all-of") || source.equals("any-of")) for (String x : s.getStringList("statuses")) deps.add(strip(x));
            if (source.equals("not")) deps.add(strip(s.getString("status", "")));
            if (s.getString("fallback", null) != null) deps.add(strip(s.getString("fallback", "")));
            refs.put(key, deps);
            StatusSourceType type = types.get(source);
            if (type == null) {
                errors.put(key, "unknown source '" + source + "'");
                continue;
            }
            try {
                StatusCheck check = type.create(s, this::has);
                definitions.put(key, new Definition(key, source, check, s.getString("fallback", null),
                        s.getBool("unavailable", false), s.getString("description", "")));
            } catch (RuntimeException e) {
                errors.put(key, e.getMessage());
            }
        }
        validateReferences();
    }

    /** Called once all plugins are enabled: reports bad statuses and references to statuses that do not exist. */
    public void reportErrors() {
        for (Map.Entry<String, String> e : errors.entrySet()) {
            plugin.getLogger().warning("statuses.yml: status '" + e.getKey() + "' is disabled: " + e.getValue());
        }
    }

    private void validateReferences() {
        for (Definition d : definitions.values()) {
            if (d.fallback() != null && !exists(d.fallback())) {
                errors.put(d.name(), "fallback status '" + d.fallback() + "' does not exist (treated as unavailable)");
            }
        }
        // A loop between combinations (or fallbacks) can never be answered: report and disable it at load. The
        // runtime guard in compute() still stops any loop that slips through (for example via nearby-player).
        Set<String> finished = new HashSet<>();
        for (String name : new ArrayList<>(refs.keySet())) findLoops(name, new ArrayList<>(), finished);
        for (String name : errors.keySet()) if (errors.get(name).startsWith("it refers back to itself")) definitions.remove(name);
    }

    private void findLoops(String name, List<String> path, Set<String> finished) {
        int at = path.indexOf(name);
        if (at >= 0) {
            List<String> loop = new ArrayList<>(path.subList(at, path.size()));
            loop.add(name);
            String desc = String.join(" -> ", loop);
            for (String m : path.subList(at, path.size())) errors.putIfAbsent(m, "it refers back to itself (" + desc + "), so it can never be answered");
            return;
        }
        if (finished.contains(name) || !refs.containsKey(name)) return;
        path.add(name);
        for (String r : refs.get(name)) findLoops(r, path, finished);
        path.removeLast();
        finished.add(name);
    }

    public boolean exists(String name) {
        String n = strip(name);
        return definitions.containsKey(n);
    }

    private static String strip(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.startsWith("!") ? n.substring(1).trim() : n;
    }

    public Set<String> names() {
        return definitions.keySet();
    }

    public Definition definition(String name) {
        return definitions.get(strip(name));
    }

    public Map<String, String> errors() {
        return errors;
    }

    /** Tests a status by name; {@code !name} negates. Unknown statuses are false (and warned about once). */
    public boolean has(Player player, String nameSpec) {
        if (player == null || nameSpec == null || nameSpec.isBlank()) return false;
        String spec = nameSpec.trim().toLowerCase(Locale.ROOT);
        boolean negate = spec.startsWith("!");
        String name = negate ? spec.substring(1).trim() : spec;
        boolean value = evaluate(player, name);
        return negate != value;
    }

    /** True if the player has every listed status. */
    public boolean hasAll(Player player, List<String> names) {
        for (String n : names) if (!has(player, n)) return false;
        return true;
    }

    /** The first listed status the player has, or null. */
    public String firstActive(Player player, List<String> names) {
        for (String n : names) if (has(player, n)) return n;
        return null;
    }

    private boolean evaluate(Player player, String name) {
        int tick = Bukkit.isPrimaryThread() ? Bukkit.getCurrentTick() : -2;
        if (tick >= 0) {
            if (tick != cacheTick) {
                cache.clear();
                cacheTick = tick;
            }
            Boolean cached = cache.getOrDefault(player.getUniqueId(), Map.of()).get(name);
            if (cached != null) return cached;
        }
        boolean result = compute(player, name);
        if (tick >= 0) cache.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(name, result);
        return result;
    }

    private boolean compute(Player player, String name) {
        Definition def = definitions.get(name);
        if (def == null) {
            if (warnedUnknown.add(name) && !errors.containsKey(name)) {
                plugin.getLogger().warning("A config refers to unknown status '" + name + "'. It counts as never active.");
            }
            return false;
        }
        Deque<String> stack = evaluating.get();
        if (stack.contains(name)) {
            if (warnedUnknown.add("cycle:" + name)) {
                plugin.getLogger().warning("Status '" + name + "' refers back to itself through " + stack + ", so that loop counts as false.");
            }
            return false;
        }
        stack.push(name);
        try {
            Boolean v;
            try {
                v = def.check().test(player);
            } catch (RuntimeException e) {
                if (warnedUnknown.add("error:" + name)) plugin.getLogger().warning("Status '" + name + "' check failed: " + e);
                v = null;
            }
            if (v != null) return v;
            if (def.fallback() != null && definitions.containsKey(strip(def.fallback()))) return has(player, def.fallback());
            return def.unavailable();
        } finally {
            stack.pop();
        }
    }

    public String format(boolean value) {
        return value ? trueText : falseText;
    }

    /** Statuses the player currently has, for /hca status. */
    public List<String> activeFor(Player player) {
        List<String> out = new ArrayList<>();
        for (String n : definitions.keySet()) if (has(player, n)) out.add(n);
        return out;
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }
}
