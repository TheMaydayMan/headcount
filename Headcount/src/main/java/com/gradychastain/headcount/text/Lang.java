package com.gradychastain.headcount.text;

import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Language file access. Missing keys fall back to the bundled en_US.yml, then to the key itself. */
public final class Lang {
    private final JavaPlugin plugin;
    private final Text text;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();
    private final Set<String> warned = new HashSet<>();
    private String language = "en_US";

    public Lang(JavaPlugin plugin, Text text) {
        this.plugin = plugin;
        this.text = text;
    }

    public void load(String language) {
        this.language = language;
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!new File(folder, "en_US.yml").exists()) plugin.saveResource("lang/en_US.yml", false);
        try (InputStream in = plugin.getResource("lang/en_US.yml")) {
            if (in != null) fallback = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read bundled en_US.yml: " + e.getMessage());
        }
        File file = new File(folder, language + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + language + ".yml not found; using en_US.");
            file = new File(folder, "en_US.yml");
        }
        messages = YamlConfiguration.loadConfiguration(file);
        warned.clear();
        text.setPrefix(raw("prefix"));
    }

    public String language() {
        return language;
    }

    /** Raw MiniMessage template; list values are joined with newlines. Returns the key if missing. */
    public String raw(String key) {
        Object v = messages.get(key);
        if (v == null) v = fallback.get(key);
        if (v == null) {
            if (warned.add(key)) plugin.getLogger().warning("Missing language key: " + key);
            return key;
        }
        if (v instanceof List<?> l) return String.join("\n", l.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }

    /** Raw template or null when the key does not exist in either file (no warning). */
    public String optional(String key) {
        Object v = messages.get(key);
        if (v == null) v = fallback.get(key);
        if (v == null) return null;
        if (v instanceof List<?> l) return String.join("\n", l.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }

    public boolean has(String key) {
        return messages.contains(key) || fallback.contains(key);
    }

    public List<String> lines(String key) {
        Object v = messages.get(key);
        if (v == null) v = fallback.get(key);
        if (v instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        if (v == null) {
            if (warned.add(key)) plugin.getLogger().warning("Missing language key: " + key);
            return List.of(key);
        }
        return List.of(String.valueOf(v).split("\n", -1));
    }

    public Component get(String key, OfflinePlayer subject, Vars vars) {
        return text.render(raw(key), subject, vars);
    }

    public Component get(String key, Vars vars) {
        return get(key, null, vars);
    }

    public Component get(String key) {
        return get(key, null, null);
    }

    public String plain(String key, OfflinePlayer subject, Vars vars) {
        return text.plainRendered(raw(key), subject, vars);
    }

    /** Sends a message; an empty string in the language file disables that message. */
    public void send(CommandSender to, String key, Vars vars) {
        if (to == null) return;
        String template = raw(key);
        if (template.isEmpty()) return;
        to.sendMessage(text.render(template, to instanceof Player p ? p : null, vars));
    }

    public void send(CommandSender to, String key) {
        send(to, key, (Vars) null);
    }

    public void send(CommandSender to, String key, Object... keyValues) {
        send(to, key, Vars.of(keyValues));
    }

    public void sendTemplate(CommandSender to, String template, Vars vars) {
        if (to == null || template == null || template.isEmpty()) return;
        to.sendMessage(text.render(template, to instanceof Player p ? p : null, vars));
    }

    public void actionBar(Player to, String key, Vars vars) {
        String template = raw(key);
        if (!template.isEmpty()) to.sendActionBar(text.render(template, to, vars));
    }

    public Text text() {
        return text;
    }
}
