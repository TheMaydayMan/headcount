package com.gradychastain.headcount.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A read-only view over either a Bukkit {@link ConfigurationSection} or a plain map (as found inside YAML lists).
 * Keys are case-sensitive; dotted paths are supported. Numbers parse leniently from strings.
 */
public final class Cfg implements com.gradychastain.headcount.api.ConfigView {
    public static final Cfg EMPTY = new Cfg(Map.of(), "");

    private final Map<String, Object> map;
    private final String path;

    private Cfg(Map<String, Object> map, String path) {
        this.map = map;
        this.path = path;
    }

    public static Cfg of(Object raw) {
        return of(raw, "");
    }

    public static Cfg of(Object raw, String path) {
        if (raw instanceof Cfg c) return c;
        if (raw instanceof ConfigurationSection s) return new Cfg(toPlain(s), path.isEmpty() ? s.getCurrentPath() : path);
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), plain(v)));
            return new Cfg(out, path);
        }
        return new Cfg(Map.of(), path);
    }

    private static Map<String, Object> toPlain(ConfigurationSection s) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) out.put(k, plain(s.get(k)));
        return out;
    }

    private static Object plain(Object v) {
        if (v instanceof ConfigurationSection s) return toPlain(s);
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), plain(val)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(plain(o));
            return out;
        }
        return v;
    }

    /** Where this section lives, for error messages. */
    public String path() {
        return path;
    }

    public String child(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    public Map<String, Object> raw() {
        return Collections.unmodifiableMap(map);
    }

    public Set<String> keys() {
        return map.keySet();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Object get(String key) {
        if (map.containsKey(key)) return map.get(key);
        int dot = key.indexOf('.');
        if (dot > 0) {
            Object head = map.get(key.substring(0, dot));
            if (head instanceof Map<?, ?>) return Cfg.of(head).get(key.substring(dot + 1));
        }
        return null;
    }

    public boolean has(String key) {
        return get(key) != null;
    }

    public String getString(String key, String def) {
        Object v = get(key);
        if (v == null) return def;
        if (v instanceof List<?> l) return String.join("\n", l.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public int getInt(String key, int def) {
        Object v = get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return (int) Math.round(Double.parseDouble(v.toString().trim()));
            } catch (NumberFormatException ignored) {
                // use default
            }
        }
        return def;
    }

    public long getLong(String key, long def) {
        Object v = get(key);
        if (v instanceof Number n) return n.longValue();
        if (v != null) {
            try {
                return Math.round(Double.parseDouble(v.toString().trim()));
            } catch (NumberFormatException ignored) {
                // use default
            }
        }
        return def;
    }

    public double getDouble(String key, double def) {
        Object v = get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try {
                return Double.parseDouble(v.toString().trim());
            } catch (NumberFormatException ignored) {
                // use default
            }
        }
        return def;
    }

    public boolean getBool(String key, boolean def) {
        Object v = get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) {
            String s = v.toString().trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("yes") || s.equals("on")) return true;
            if (s.equals("false") || s.equals("no") || s.equals("off")) return false;
        }
        return def;
    }

    /** Duration in milliseconds from values such as {@code 30s} or {@code 1h}; plain numbers are seconds. */
    public long getMillis(String key, long def) {
        return Durations.parseMillis(get(key), def);
    }

    public List<String> getStringList(String key) {
        Object v = get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object o : l) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        String s = v.toString();
        return s.isEmpty() ? List.of() : List.of(s);
    }

    public List<Object> getList(String key) {
        Object v = get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> l) return new ArrayList<>(l);
        return List.of(v);
    }

    public List<Integer> getIntList(String key) {
        List<Integer> out = new ArrayList<>();
        for (Object o : getList(key)) {
            String s = String.valueOf(o).trim();
            int dash = s.indexOf('-', 1);
            try {
                if (dash > 0) {
                    int a = Integer.parseInt(s.substring(0, dash).trim());
                    int b = Integer.parseInt(s.substring(dash + 1).trim());
                    for (int i = Math.min(a, b); i <= Math.max(a, b); i++) out.add(i);
                } else {
                    out.add((int) Math.round(Double.parseDouble(s)));
                }
            } catch (NumberFormatException ignored) {
                // skip invalid entry
            }
        }
        return out;
    }

    public Cfg getSection(String key) {
        Object v = get(key);
        return v instanceof Map<?, ?> ? Cfg.of(v, child(key)) : new Cfg(Map.of(), child(key));
    }

    public boolean isSection(String key) {
        return get(key) instanceof Map<?, ?>;
    }

    public boolean isList(String key) {
        return get(key) instanceof List<?>;
    }

    /** Returns a copy with {@code overrides} merged on top (one level deep, nested maps merged recursively). */
    public Cfg merge(Cfg overrides) {
        Map<String, Object> out = new LinkedHashMap<>(map);
        for (Map.Entry<String, Object> e : overrides.map.entrySet()) {
            Object base = out.get(e.getKey());
            if (base instanceof Map<?, ?> && e.getValue() instanceof Map<?, ?>) {
                out.put(e.getKey(), Cfg.of(base).merge(Cfg.of(e.getValue())).map);
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
        return new Cfg(out, path);
    }

    /** Returns a copy with one key replaced. */
    public Cfg with(String key, Object value) {
        Map<String, Object> out = new LinkedHashMap<>(map);
        out.put(key, value);
        return new Cfg(out, path);
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
