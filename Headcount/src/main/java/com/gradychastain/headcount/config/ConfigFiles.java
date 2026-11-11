package com.gradychastain.headcount.config;

import com.gradychastain.headcount.util.Cfg;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads every Headcount config file. Settings files (config, trade, scoring, integrations) are merged over the
 * bundled defaults so a missing option falls back to its default. Collection files (statuses, actions, outcomes,
 * shop, titles) are used exactly as written so deleting an entry really removes it.
 */
public final class ConfigFiles {
    public static final List<String> FILES = List.of("config.yml", "statuses.yml", "trade.yml", "scoring.yml", "actions.yml",
            "outcomes.yml", "shop.yml", "titles.yml", "integrations.yml");
    private static final Set<String> MERGED = Set.of("config.yml", "trade.yml", "scoring.yml", "integrations.yml");
    /** Subtrees that replace the default wholesale instead of merging key by key. */
    private static final Map<String, Set<String>> ATOMIC = Map.of("config.yml", Set.of("hooks"));

    private final JavaPlugin plugin;
    private final Map<String, Cfg> loaded = new LinkedHashMap<>();
    private final Map<String, Cfg> defaults = new LinkedHashMap<>();

    public ConfigFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loaded.clear();
        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) plugin.saveResource(name, false);
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
            } catch (Exception e) {
                plugin.getLogger().severe("Could not load " + name + " (" + e.getMessage() + "); using bundled defaults for it.");
                yaml = bundled(name);
            }
            Cfg actual = Cfg.of(yaml, "");
            Cfg def = Cfg.of(bundled(name), "");
            defaults.put(name, def);
            if (MERGED.contains(name)) {
                Cfg merged = def.merge(actual);
                for (String atomic : ATOMIC.getOrDefault(name, Set.of())) {
                    if (actual.has(atomic)) merged = merged.with(atomic, actual.get(atomic));
                }
                loaded.put(name, merged);
            } else {
                loaded.put(name, actual);
            }
            int have = actual.getInt("config-version", 0);
            int want = def.getInt("config-version", 0);
            if (want > 0 && have < want) {
                plugin.getLogger().warning(name + " is config-version " + have + " but this version of Headcount expects " + want
                        + ". Missing options use their defaults. Compare it with the bundled file to see what's new.");
            }
        }
    }

    private YamlConfiguration bundled(String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    public Cfg get(String file) {
        return loaded.getOrDefault(file, Cfg.EMPTY);
    }

    public Cfg defaults(String file) {
        return defaults.getOrDefault(file, Cfg.EMPTY);
    }

    public Cfg main() { return get("config.yml"); }
    public Cfg statuses() { return get("statuses.yml"); }
    public Cfg trade() { return get("trade.yml"); }
    public Cfg scoring() { return get("scoring.yml"); }
    public Cfg actions() { return get("actions.yml"); }
    public Cfg outcomes() { return get("outcomes.yml"); }
    public Cfg shop() { return get("shop.yml"); }
    public Cfg titles() { return get("titles.yml"); }
    public Cfg integrations() { return get("integrations.yml"); }
}
