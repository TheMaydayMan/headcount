package com.gradychastain.headcount.sidebar;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Headcount's own sidebar. Server-wide it can be switched off (so another scoreboard plugin can show Headcount's
 * placeholders instead); each player toggles it with /hc sidebar and it starts off. Every line is a team prefix on
 * a unique invisible entry, the approach Bedrock clients (through Geyser) render correctly; Java clients also get
 * a blank number format so no score numbers show.
 */
public final class SidebarManager {
    private static final String[] ENTRIES = new String[15];

    static {
        String codes = "0123456789abcdef";
        for (int i = 0; i < 15; i++) ENTRIES[i] = "§" + codes.charAt(i) + "§r";
    }

    private final HeadcountPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Scoreboard> previous = new HashMap<>();
    private int task = -1;

    public SidebarManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private Cfg cfg() {
        return plugin.configs().main().getSection("sidebar");
    }

    public boolean serverEnabled() {
        return cfg().getBool("enabled", true);
    }

    public void reload() {
        if (task != -1) Bukkit.getScheduler().cancelTask(task);
        task = -1;
        if (!serverEnabled()) {
            for (UUID id : List.copyOf(boards.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) hide(p);
            }
            return;
        }
        long interval = Math.max(1, cfg().getMillis("update-interval", 1000) / 50);
        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::updateAll, interval, interval);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.players().get(p.getUniqueId());
            if (d != null && d.sidebar) show(p);
        }
    }

    public boolean isShown(Player p) {
        return boards.containsKey(p.getUniqueId());
    }

    /** /hc sidebar. @return the new state */
    public boolean toggle(Player p) {
        PlayerData d = plugin.players().get(p);
        d.sidebar = !d.sidebar;
        plugin.playerStore().set(p.getUniqueId(), "sidebar", d.sidebar);
        if (d.sidebar) show(p);
        else hide(p);
        return d.sidebar;
    }

    public void onJoin(Player p) {
        PlayerData d = plugin.players().get(p.getUniqueId());
        if (serverEnabled() && d != null && d.sidebar) show(p);
    }

    /** Shows or hides the sidebar to match the player's setting (after a hard reset replaced their data). */
    public void resync(Player p) {
        PlayerData d = plugin.players().get(p.getUniqueId());
        boolean want = serverEnabled() && d != null && d.sidebar;
        if (want) show(p);
        else if (isShown(p)) hide(p);
    }

    public void onQuit(Player p) {
        boards.remove(p.getUniqueId());
        previous.remove(p.getUniqueId());
    }

    private void show(Player p) {
        if (!serverEnabled() || boards.containsKey(p.getUniqueId())) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("headcount", Criteria.DUMMY, Component.text("Headcount"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        previous.put(p.getUniqueId(), p.getScoreboard());
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        update(p, sb);
    }

    private void hide(Player p) {
        Scoreboard sb = boards.remove(p.getUniqueId());
        Scoreboard prev = previous.remove(p.getUniqueId());
        if (sb != null && p.getScoreboard() == sb) p.setScoreboard(prev != null ? prev : Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updateAll() {
        for (Map.Entry<UUID, Scoreboard> e : List.copyOf(boards.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                boards.remove(e.getKey());
                continue;
            }
            try {
                update(p, e.getValue());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Sidebar update failed for " + p.getName() + ": " + ex.getMessage());
            }
        }
    }

    private void update(Player p, Scoreboard sb) {
        Objective obj = sb.getObjective("headcount");
        if (obj == null) return;
        Vars v = Vars.empty();
        obj.displayName(plugin.text().render(cfg().getString("title", "<gold><bold>Headcount"), p, v));
        List<String> lines = plugin.cycles().current() == null ? cfg().getStringList("lines-before-start") : cfg().getStringList("lines");
        int n = Math.min(15, lines.size());
        for (int i = 0; i < 15; i++) {
            String entry = ENTRIES[i];
            Team team = sb.getTeam("hc" + i);
            if (i >= n) {
                if (team != null) team.unregister();
                sb.resetScores(entry);
                continue;
            }
            if (team == null) {
                team = sb.registerNewTeam("hc" + i);
                team.addEntry(entry);
            }
            team.prefix(plugin.text().render(lines.get(i), p, v));
            obj.getScore(entry).setScore(n - i);
        }
    }

    public void shutdown() {
        if (task != -1) Bukkit.getScheduler().cancelTask(task);
        task = -1;
        for (UUID id : List.copyOf(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) hide(p);
        }
        boards.clear();
    }
}
