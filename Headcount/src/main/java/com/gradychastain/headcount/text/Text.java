package com.gradychastain.headcount.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders configurable text. Order of resolution:
 * <ol>
 *   <li>{@code %headcount_...%} placeholders, resolved internally (no PlaceholderAPI needed);</li>
 *   <li>any other {@code %...%} placeholder through PlaceholderAPI when it is installed;</li>
 *   <li>MiniMessage parsing with {@code <name>} variables from {@link Vars} plus the global {@code <prefix>}.</li>
 * </ol>
 */
public final class Text {
    private static final Pattern PERCENT = Pattern.compile("%([A-Za-z0-9]+)_([^%\\s]*)%");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Logger logger;
    private BiFunction<OfflinePlayer, String, PlaceholderValue> internal = (p, s) -> null;
    private BiFunction<OfflinePlayer, String, String> external = null;
    private String prefix = "";

    public Text(Logger logger) {
        this.logger = logger;
    }

    public void setInternal(BiFunction<OfflinePlayer, String, PlaceholderValue> internal) {
        this.internal = internal;
    }

    /** Sets the PlaceholderAPI bridge, or null when PlaceholderAPI is not used. */
    public void setExternal(BiFunction<OfflinePlayer, String, String> external) {
        this.external = external;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public MiniMessage mini() {
        return mm;
    }

    public Component render(String template, OfflinePlayer subject, Vars vars) {
        if (template == null || template.isEmpty()) return Component.empty();
        String resolved = resolvePercent(template, subject, true);
        TagResolver tags = TagResolver.resolver(Placeholder.parsed("prefix", prefix), vars == null ? TagResolver.empty() : vars.resolver());
        try {
            return mm.deserialize(resolved, tags);
        } catch (RuntimeException e) {
            logger.warning("Could not parse MiniMessage text '" + template + "': " + e.getMessage());
            return Component.text(mm.stripTags(resolved));
        }
    }

    public Component render(String template, Vars vars) {
        return render(template, null, vars);
    }

    /** Renders a line for item names and lore: italics are off unless the text asks for them. */
    public Component item(String template, OfflinePlayer subject, Vars vars) {
        return render(template, subject, vars).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Renders each line, splitting embedded newlines; lines that render empty from an empty template are kept. */
    public List<Component> itemLines(List<String> lines, OfflinePlayer subject, Vars vars) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            for (String part : line.split("\n", -1)) {
                // A line that is only a list variable (e.g. "<outcomes_lines>") expands to several lines.
                out.add(item(part, subject, vars));
            }
        }
        return out;
    }

    /**
     * Resolves placeholders and {@code <var>} tokens to plain text (no formatting). Used for console commands,
     * Discord messages and anywhere formatting is meaningless.
     */
    public String plain(String template, OfflinePlayer subject, Vars vars) {
        if (template == null) return "";
        String s = resolvePercent(template, subject, false);
        if (vars != null) {
            for (var e : vars.asMap().entrySet()) {
                s = s.replace("<" + e.getKey() + ">", Vars.plainOf(e.getValue()));
            }
        }
        s = s.replace("<prefix>", mm.stripTags(prefix));
        return s;
    }

    /** Fully rendered text as plain string (formatting dropped). */
    public String plainRendered(String template, OfflinePlayer subject, Vars vars) {
        return PlainTextComponentSerializer.plainText().serialize(render(template, subject, vars));
    }

    public String legacy(Component c) {
        return LEGACY_HEX.serialize(c);
    }

    public String toMini(Component c) {
        return mm.serialize(c);
    }

    public String escape(String s) {
        return mm.escapeTags(s == null ? "" : s);
    }

    /**
     * Replaces {@code %id_params%} tokens. When {@code forMini} is true the result is safe to feed to MiniMessage
     * (plain values escaped, legacy colors from other plugins converted).
     */
    public String resolvePercent(String s, OfflinePlayer subject, boolean forMini) {
        if (s.indexOf('%') < 0) return s;
        Matcher m = PERCENT.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String id = m.group(1).toLowerCase(Locale.ROOT);
            String params = m.group(2);
            String replacement = null;
            if (id.equals("headcount")) {
                PlaceholderValue v = null;
                try {
                    v = internal.apply(subject, params);
                } catch (RuntimeException e) {
                    logger.warning("Placeholder %headcount_" + params + "% failed: " + e);
                }
                if (v != null) {
                    if (forMini) replacement = v.mini() ? v.text() : mm.escapeTags(v.text());
                    else replacement = v.mini() ? mm.stripTags(v.text()) : v.text();
                }
            } else if (external != null) {
                try {
                    String out = external.apply(subject, m.group());
                    if (out != null && !out.equals(m.group())) {
                        replacement = forMini ? legacyToMini(out) : stripLegacy(out);
                    }
                } catch (RuntimeException e) {
                    logger.fine("PlaceholderAPI failed for " + m.group() + ": " + e);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? m.group() : replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String legacyToMini(String legacyText) {
        if (legacyText.indexOf('§') < 0 && legacyText.indexOf('&') < 0) return mm.escapeTags(legacyText);
        Component c = legacyText.indexOf('§') >= 0 ? LEGACY.deserialize(legacyText) : LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
        return mm.serialize(c);
    }

    private static String stripLegacy(String s) {
        return s.replaceAll("(?i)[§&][0-9A-FK-ORX]", "");
    }

    /** Resolves only placeholders for a numeric context (formula variables). */
    public String resolveForNumber(String placeholder, OfflinePlayer subject) {
        return stripLegacy(resolvePercent(placeholder, subject, false)).trim();
    }
}
