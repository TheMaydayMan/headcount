package com.gradychastain.headcount.title;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.integration.permission.PermissionHook;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Titles from titles.yml. Players can own several and equip one, unless an action forces one. The equipped title is
 * the {@code %headcount_title%} placeholder and can also be applied as a LuckPerms (or Vault) prefix or suffix.
 */
public final class TitleManager {
    public record Title(String id, String display, Cfg icon, java.util.List<String> description, int order) {}

    private record Forced(String title, String instance) {}

    private final HeadcountPlugin plugin;
    private final Map<String, Title> titles = new LinkedHashMap<>();
    private final Map<UUID, Forced> forced = new HashMap<>();
    private final Map<UUID, String> appliedMeta = new HashMap<>();
    /** The type and priority the entries in appliedMeta were written with, so a reload can remove them. */
    private boolean appliedSuffix;
    private int appliedPriority = Integer.MIN_VALUE;

    public TitleManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private Cfg settings() {
        return plugin.configs().titles().getSection("settings");
    }

    public void load(Cfg file) {
        titles.clear();
        Cfg t = file.getSection("titles");
        int order = 0;
        for (String id : t.keys()) {
            Cfg c = t.getSection(id);
            if (!c.getBool("enabled", true)) continue;
            titles.put(id.toLowerCase(Locale.ROOT), new Title(id.toLowerCase(Locale.ROOT), c.getString("display", id), c.getSection("icon"),
                    c.getStringList("description"), order++));
        }
    }

    public Map<String, Title> all() {
        return titles;
    }

    public Title title(String id) {
        return id == null ? null : titles.get(id.toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- ownership

    public Set<String> owned(UUID uuid) {
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) return d.titles;
        return plugin.db().sync(c -> {
            Set<String> out = new LinkedHashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT title FROM titles WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
            }
            return out;
        });
    }

    public boolean owns(UUID uuid, String id) {
        return owned(uuid).contains(id.toLowerCase(Locale.ROOT));
    }

    public boolean grant(UUID uuid, String id, String source) {
        String t = id.toLowerCase(Locale.ROOT);
        if (!titles.containsKey(t)) {
            plugin.getLogger().warning("Can't grant title '" + id + "': it isn't in titles.yml.");
            return false;
        }
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) d.titles.add(t);
        plugin.playerStore().setTitle(uuid, t, true, source);
        plugin.audit().log(AuditType.TITLE, "player", plugin.players().name(uuid) + "(" + uuid + ")", "title", t, "action", "grant", "source", source);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) plugin.lang().send(p, "titles.granted", Vars.empty().mini("title", titles.get(t).display()));
        if (settings().getBool("auto-equip-first", true) && equipped(uuid) == null) equip(uuid, t, true);
        return true;
    }

    public boolean revoke(UUID uuid, String id, String source) {
        String t = id.toLowerCase(Locale.ROOT);
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) d.titles.remove(t);
        plugin.playerStore().setTitle(uuid, t, false, source);
        if (t.equals(storedEquipped(uuid))) unequip(uuid);
        plugin.audit().log(AuditType.TITLE, "player", plugin.players().name(uuid) + "(" + uuid + ")", "title", t, "action", "revoke", "source", source);
        return true;
    }

    // ---------------------------------------------------------------- equipping

    private String storedEquipped(UUID uuid) {
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) return d.equippedTitle;
        return plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT equipped_title FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    /** The title shown for this player: a forced title, else the equipped one, else null. */
    public String equipped(UUID uuid) {
        Forced f = forced.get(uuid);
        if (f != null) return f.title();
        String e = storedEquipped(uuid);
        return e != null && titles.containsKey(e) ? e : null;
    }

    public boolean isForced(UUID uuid) {
        return forced.containsKey(uuid);
    }

    /** Equips an owned title. Fails while a title is forced. */
    public boolean equip(UUID uuid, String id, boolean silent) {
        String t = id.toLowerCase(Locale.ROOT);
        Player p = Bukkit.getPlayer(uuid);
        if (forced.containsKey(uuid)) {
            if (!silent && p != null) plugin.lang().send(p, "titles.forced");
            return false;
        }
        if (!owns(uuid, t) || !titles.containsKey(t)) return false;
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) d.equippedTitle = t;
        plugin.playerStore().set(uuid, "equipped_title", t);
        applyMeta(uuid);
        if (!silent && p != null) plugin.lang().send(p, "titles.equipped", Vars.empty().mini("title", titles.get(t).display()));
        return true;
    }

    public void unequip(UUID uuid) {
        PlayerData d = plugin.players().loaded(uuid);
        if (d != null) d.equippedTitle = null;
        plugin.playerStore().set(uuid, "equipped_title", null);
        applyMeta(uuid);
    }

    public void force(UUID uuid, String id, String instance) {
        String t = id.toLowerCase(Locale.ROOT);
        Forced cur = forced.get(uuid);
        if (cur != null && cur.title().equals(t)) return;
        forced.put(uuid, new Forced(t, instance));
        applyMeta(uuid);
    }

    public void unforce(UUID uuid, String instance) {
        Forced cur = forced.get(uuid);
        if (cur != null && (instance == null || instance.equals(cur.instance()))) {
            forced.remove(uuid);
            applyMeta(uuid);
        }
    }

    /** Rendered title for placeholders, or the configured "none" text. */
    public String displayFor(UUID uuid) {
        String t = equipped(uuid);
        if (t == null) return settings().getString("none-text", "");
        Title title = titles.get(t);
        return settings().getString("format", "<title>").replace("<title>", title == null ? t : title.display());
    }

    /** Keeps the LuckPerms (or Vault) prefix/suffix in step with the shown title. */
    public void applyMeta(UUID uuid) {
        Cfg lp = settings().getSection("permission-prefix");
        if (!lp.getBool("enabled", false)) return;
        PermissionHook h = plugin.integrations().permissions();
        if (h == null) return;
        boolean suffix = lp.getString("type", "prefix").equalsIgnoreCase("suffix");
        int priority = lp.getInt("priority", 100);
        String t = equipped(uuid);
        String text = null;
        if (t != null && titles.containsKey(t)) {
            String mini = lp.getString("format", "<title> ").replace("<title>", titles.get(t).display());
            text = LegacyComponentSerializer.legacyAmpersand().serialize(plugin.text().mini().deserialize(mini));
        }
        if (text != null && text.equals(appliedMeta.get(uuid))) return;
        h.clearMeta(uuid, suffix, priority);
        if (text == null) {
            appliedMeta.remove(uuid);
            return;
        }
        if (suffix) h.addSuffix(uuid, text, priority, null);
        else h.addPrefix(uuid, text, priority, null);
        appliedMeta.put(uuid, text);
        appliedSuffix = suffix;
        appliedPriority = priority;
    }

    /**
     * After a reload: online players' prefixes follow the new settings. Turning permission-prefix on gives them
     * their title now; turning it off (or changing the type or priority) removes what Headcount had set.
     */
    public void resyncMeta() {
        Cfg lp = settings().getSection("permission-prefix");
        boolean enabled = lp.getBool("enabled", false);
        boolean suffix = lp.getString("type", "prefix").equalsIgnoreCase("suffix");
        int priority = lp.getInt("priority", 100);
        PermissionHook h = plugin.integrations().permissions();
        boolean moved = !enabled || suffix != appliedSuffix || priority != appliedPriority;
        if (h != null && moved && appliedPriority != Integer.MIN_VALUE) {
            for (UUID id : appliedMeta.keySet()) h.clearMeta(id, appliedSuffix, appliedPriority);
        }
        appliedMeta.clear();
        if (enabled) for (Player p : Bukkit.getOnlinePlayers()) applyMeta(p.getUniqueId());
    }

    /** Hard reset: takes off every equipped and forced title, which also removes any prefix or suffix Headcount set. */
    public void unequipAll() {
        List<UUID> equipped = plugin.db().sync(c -> {
            List<UUID> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM players WHERE equipped_title IS NOT NULL"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
            return out;
        });
        Set<UUID> all = new HashSet<>(equipped);
        all.addAll(forced.keySet());
        forced.clear();
        for (UUID id : all) unequip(id);
    }

    public void onJoin(Player p) {
        appliedMeta.remove(p.getUniqueId());
        applyMeta(p.getUniqueId());
    }
}
