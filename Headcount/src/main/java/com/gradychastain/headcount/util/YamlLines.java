package com.gradychastain.headcount.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes one value in a YAML file's text without re-dumping it, so comments, blank lines, key order and quoting
 * everywhere else stay as the owner left them. Handles block mappings (nested by indentation) with a scalar or
 * flow-list value on the key's line, and block lists under a key (rewritten as one flow list). Flow mappings and
 * multi-line scalars are not edited: {@link #set} returns false and the caller asks the owner to make the change.
 */
public final class YamlLines {
    private YamlLines() {}

    /** A mapping key line: indent, key (optionally quoted), and the rest after the colon. */
    private static final Pattern KEY = Pattern.compile("^(\\s*)(\"[^\"]*\"|'[^']*'|[^\\s#'\"\\-][^:#]*?|-[^\\s:#][^:#]*?)\\s*:(\\s.*|)$");

    /** Where a key sits: its line, and the indexes of block-list item lines that belong to it. */
    private record Found(int line, String indent, String key, String rest, List<Integer> blockItems) {}

    /** Splits text into lines, keeping the line ending style for {@link #join}. */
    public static List<String> lines(String text) {
        String[] parts = text.split("\r?\n", -1);
        List<String> out = new ArrayList<>(List.of(parts));
        return out;
    }

    public static String join(List<String> lines, String original) {
        String nl = original.contains("\r\n") ? "\r\n" : "\n";
        return String.join(nl, lines);
    }

    private static Found find(List<String> lines, String dottedPath) {
        String[] want = dottedPath.split("\\.");
        List<int[]> stackIndent = new ArrayList<>();
        List<String> stackKey = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("---")) continue;
            if (trimmed.startsWith("- ") || trimmed.equals("-")) continue; // list items are values, not keys
            Matcher m = KEY.matcher(line);
            if (!m.matches()) continue;
            int indent = m.group(1).length();
            while (!stackIndent.isEmpty() && stackIndent.getLast()[0] >= indent) {
                stackIndent.removeLast();
                stackKey.removeLast();
            }
            String key = unquote(m.group(2).strip());
            stackIndent.add(new int[]{indent});
            stackKey.add(key);
            if (stackKey.size() != want.length) continue;
            boolean match = true;
            for (int k = 0; k < want.length; k++) {
                if (!stackKey.get(k).equals(want[k])) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            List<Integer> items = new ArrayList<>();
            if (valuePart(m.group(3)).isEmpty()) {
                // A block value follows: collect "- item" lines indented at least as far as the key.
                for (int j = i + 1; j < lines.size(); j++) {
                    String l = lines.get(j);
                    String t = l.strip();
                    if (t.isEmpty() || t.startsWith("#")) continue;
                    int ind = l.length() - l.stripLeading().length();
                    if (ind < indent || (ind == indent && !t.startsWith("-"))) break;
                    if (t.startsWith("- ") || t.equals("-")) {
                        if (t.substring(1).strip().contains(":")) return new Found(i, m.group(1), key, m.group(3), null); // list of maps
                        items.add(j);
                    } else {
                        return new Found(i, m.group(1), key, m.group(3), null); // a nested mapping, not a value we edit
                    }
                }
            }
            return new Found(i, m.group(1), key, m.group(3), items);
        }
        return null;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** The value text after the colon, without a trailing comment. */
    private static String valuePart(String rest) {
        return rest.substring(0, commentStart(rest)).strip();
    }

    /** Index in {@code rest} where a trailing " # comment" starts (outside quotes), or rest.length(). */
    private static int commentStart(String rest) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '\'' && !dbl) single = !single;
            else if (c == '"' && !single) dbl = !dbl;
            else if (c == '#' && !single && !dbl && (i == 0 || Character.isWhitespace(rest.charAt(i - 1)))) {
                // Keep the whitespace before the comment with the comment.
                int s = i;
                while (s > 0 && Character.isWhitespace(rest.charAt(s - 1))) s--;
                return s;
            }
        }
        return rest.length();
    }

    /**
     * The value at a dotted path as written: a String for a scalar (quotes removed), a List of Strings for a flow or
     * block list, or null when the key isn't in the file or holds something else (a mapping, a flow mapping).
     */
    public static Object get(List<String> lines, String dottedPath) {
        Found f = find(lines, dottedPath);
        if (f == null) return null;
        String v = valuePart(f.rest());
        if (v.isEmpty()) {
            if (f.blockItems() == null || f.blockItems().isEmpty()) return f.blockItems() == null ? null : List.of();
            List<String> out = new ArrayList<>();
            for (int j : f.blockItems()) out.add(unquote(valuePart(" " + lines.get(j).strip().substring(1))));
            return out;
        }
        if (v.startsWith("{")) return null;
        if (v.startsWith("[")) {
            if (!v.endsWith("]")) return null;
            List<String> out = new ArrayList<>();
            for (String part : v.substring(1, v.length() - 1).split(",")) {
                String p = unquote(part.strip());
                if (!p.isEmpty()) out.add(p);
            }
            return out;
        }
        if (v.startsWith("|") || v.startsWith(">")) return null;
        return unquote(v);
    }

    /**
     * Sets the value at a dotted path, keeping the line's indentation and trailing comment. {@code newValue} is
     * written as given (a plain word or a flow list like {@code [a, b]}). Returns false, changing nothing, when the
     * key isn't in the file or its value can't be edited safely.
     */
    public static boolean set(List<String> lines, String dottedPath, String newValue) {
        Found f = find(lines, dottedPath);
        if (f == null) return false;
        String rest = f.rest();
        String value = valuePart(rest);
        if (value.startsWith("{") || value.startsWith("|") || value.startsWith(">")) return false;
        if (value.startsWith("[") && !value.endsWith("]")) return false; // a flow list over several lines
        if (value.isEmpty() && f.blockItems() == null) return false;     // a nested mapping
        String comment = rest.substring(commentStart(rest));
        String line = lines.get(f.line());
        String keyPart = line.substring(0, line.length() - rest.length());
        lines.set(f.line(), keyPart + " " + newValue + comment);
        if (value.isEmpty() && f.blockItems() != null) {
            List<Integer> items = f.blockItems();
            for (int k = items.size() - 1; k >= 0; k--) lines.remove((int) items.get(k));
        }
        return true;
    }
}
