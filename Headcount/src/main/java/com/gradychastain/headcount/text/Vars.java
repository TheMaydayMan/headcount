package com.gradychastain.headcount.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Named values for text templates. In MiniMessage text a value is used as {@code <name>}.
 * Plain values (strings, numbers) are inserted literally and never parsed as tags, so player names cannot inject formatting.
 * Values added with {@link #mini(String, String)} are parsed as MiniMessage (used for titles and other pre-formatted text).
 */
public final class Vars {
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_-]+");

    /** A MiniMessage string that should be parsed when inserted. */
    public record Mini(String text) {}

    private final Map<String, Object> values = new LinkedHashMap<>();

    public static Vars of(Object... keyValues) {
        Vars v = new Vars();
        for (int i = 0; i + 1 < keyValues.length; i += 2) v.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        return v;
    }

    public static Vars empty() {
        return new Vars();
    }

    public Vars put(String key, Object value) {
        String k = key.toLowerCase(Locale.ROOT);
        if (!VALID_KEY.matcher(k).matches()) return this;
        values.put(k, value == null ? "" : value);
        return this;
    }

    public Vars mini(String key, String miniMessage) {
        return put(key, new Mini(miniMessage == null ? "" : miniMessage));
    }

    /** A tag with arguments, such as {@code <score:green:red:gray>}; the key only names it in this set. */
    public Vars tag(String key, TagResolver resolver) {
        return put(key, resolver);
    }

    public Vars putAll(Vars other) {
        if (other != null) values.putAll(other.values);
        return this;
    }

    public Vars copy() {
        return new Vars().putAll(this);
    }

    public boolean has(String key) {
        return values.containsKey(key.toLowerCase(Locale.ROOT));
    }

    public Object get(String key) {
        return values.get(key.toLowerCase(Locale.ROOT));
    }

    public Map<String, Object> asMap() {
        return values;
    }

    /** Plain-text form of a value (MiniMessage stripped). */
    public String plain(String key) {
        return plainOf(values.get(key.toLowerCase(Locale.ROOT)));
    }

    public static String plainOf(Object v) {
        if (v == null || v instanceof TagResolver) return "";
        if (v instanceof Component c) return PlainTextComponentSerializer.plainText().serialize(c);
        if (v instanceof Mini m) return MiniMessage.miniMessage().stripTags(m.text());
        if (v instanceof Double d && d == Math.rint(d) && !d.isInfinite()) return Long.toString(d.longValue());
        if (v instanceof Float f && f == Math.rint(f) && !f.isInfinite()) return Long.toString(f.longValue());
        return String.valueOf(v);
    }

    /** Numeric value of a variable, or null when it is not a number. */
    public Double number(String key) {
        Object v = values.get(key.toLowerCase(Locale.ROOT));
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v == null) return null;
        try {
            return Double.parseDouble(plainOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public TagResolver resolver() {
        List<TagResolver> list = new ArrayList<>(values.size());
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v instanceof TagResolver r) list.add(r);
            else if (v instanceof Component c) list.add(Placeholder.component(e.getKey(), c));
            else if (v instanceof Mini m) list.add(Placeholder.parsed(e.getKey(), m.text()));
            else list.add(Placeholder.unparsed(e.getKey(), plainOf(v)));
        }
        return TagResolver.resolver(list);
    }

    /** Flattens to strings for persistence. MiniMessage values are prefixed with {@code mm:}. */
    public Map<String, Object> toStorable() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v instanceof TagResolver) continue;   // live tags are rebuilt, never stored
            if (v instanceof Component c) out.put(e.getKey(), "mm:" + MiniMessage.miniMessage().serialize(c));
            else if (v instanceof Mini m) out.put(e.getKey(), "mm:" + m.text());
            else if (v instanceof Number || v instanceof Boolean) out.put(e.getKey(), v);
            else out.put(e.getKey(), String.valueOf(v));
        }
        return out;
    }

    public static Vars fromStorable(Map<String, Object> map) {
        Vars v = new Vars();
        if (map == null) return v;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object val = e.getValue();
            if (val instanceof String s && s.startsWith("mm:")) v.mini(e.getKey(), s.substring(3));
            else v.put(e.getKey(), val);
        }
        return v;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
