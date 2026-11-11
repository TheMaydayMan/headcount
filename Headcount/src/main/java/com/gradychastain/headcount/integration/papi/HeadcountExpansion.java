package com.gradychastain.headcount.integration.papi;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.text.PlaceholderValue;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Exposes Headcount's placeholders to other plugins as {@code %headcount_<name>%}. */
public final class HeadcountExpansion extends PlaceholderExpansion {
    private final HeadcountPlugin plugin;

    public HeadcountExpansion(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "headcount"; }
    @Override public String getAuthor() { return "Grady Chastain"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        PlaceholderValue v = plugin.placeholders().resolve(player, params);
        if (v == null) return null;
        if (!v.mini()) return v.text();
        var cfg = plugin.configs().integrations();
        // placeholders.placeholderapi.expansion-format; before 1.1 it was placeholderapi.expansion-format
        String format = cfg.has("placeholderapi.expansion-format") ? cfg.getString("placeholderapi.expansion-format", "legacy")
                : cfg.getString("placeholders.placeholderapi.expansion-format", "legacy");
        return switch (format.toLowerCase(java.util.Locale.ROOT)) {
            case "minimessage" -> v.text();
            case "plain" -> plugin.text().mini().stripTags(v.text());
            default -> plugin.text().legacy(plugin.text().mini().deserialize(v.text()));
        };
    }
}
