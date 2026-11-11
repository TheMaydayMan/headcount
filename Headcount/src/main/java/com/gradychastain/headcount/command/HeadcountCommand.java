package com.gradychastain.headcount.command;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.history.HistoryGui;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.scoring.LiveStats;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.title.TitlesGui;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.vault.VaultTally;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /headcount (/hc): the vault, and every player subcommand. {@code /hc admin ...} forwards to the admin command. */
public final class HeadcountCommand implements TabExecutor {
    /** Player subcommands in help order, with their permissions. */
    static final List<String[]> PLAYER = List.of(
            new String[]{"", "headcount.vault"}, new String[]{"shop", "headcount.shop"}, new String[]{"titles", "headcount.titles"},
            new String[]{"points", "headcount.points"}, new String[]{"score", "headcount.score"}, new String[]{"top", "headcount.top"},
            new String[]{"history", "headcount.history"}, new String[]{"sidebar", "headcount.sidebar"}, new String[]{"effects", "headcount.effects"},
            new String[]{"help", "headcount.help"});

    private final HeadcountPlugin plugin;
    private final AdminCommand admin;

    public HeadcountCommand(HeadcountPlugin plugin, AdminCommand admin) {
        this.plugin = plugin;
        this.admin = admin;
    }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node)) return true;
        plugin.lang().send(s, "general.no-permission");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin.onCommand(sender, command, label + " admin", Arrays.copyOfRange(args, 1, args.length));
        if (sub.equals("help") || sub.equals("?")) {
            help(sender, label);
            return true;
        }
        if (!(sender instanceof Player p)) {
            plugin.lang().send(sender, "general.players-only");
            return true;
        }
        if (!plugin.isLoaded()) {
            plugin.lang().send(sender, "general.loading");
            return true;
        }
        switch (sub) {
            case "", "vault" -> {
                if (perm(p, "headcount.vault")) plugin.vaults().open(p, p.getUniqueId(), args.length > 1 ? parsePage(args[1]) : 0, false, true);
            }
            case "shop" -> {
                if (perm(p, "headcount.shop")) plugin.shop().open(p, args.length > 1 ? args[1] : null, 0);
            }
            case "titles" -> {
                if (perm(p, "headcount.titles")) new TitlesGui(plugin, p, 0).open();
            }
            case "points" -> {
                if (!perm(p, "headcount.points")) return true;
                PlayerData d = plugin.players().get(p);
                plugin.lang().send(p, "points.balance", Vars.of("points", d.points, "points_next", d.pointsNext,
                        "rollover", Formats.number(d.rolloverOverride != null ? d.rolloverOverride : plugin.configs().main().getDouble("points.rollover-percent", 0))));
            }
            case "score" -> {
                if (!perm(p, "headcount.score")) return true;
                Cycle c = plugin.cycles().current();
                if (c == null) {
                    plugin.lang().send(p, "cycle.not-started");
                    return true;
                }
                Map<String, Double> vars = plugin.scores().liveVariables(p);
                int score = plugin.scores().previewScore(vars, p);
                VaultTally t = plugin.vaults().tally(p.getUniqueId());
                PlayerData d = plugin.players().get(p);
                plugin.lang().send(p, "score.preview", Vars.of("score", score, "unique", t.unique(), "total", t.total(), "own", t.own(),
                        "active", Formats.number(vars.getOrDefault("active", 0.0)), "rank", Formats.number(vars.getOrDefault("rank", 0.0)),
                        "time_left", Formats.duration(c.remainingMs()), "formula", plugin.scores().formula().source())
                        .mini("is_active", d.stats != null && d.stats.active ? plugin.lang().raw("score.you-are-active") : plugin.lang().raw("score.you-are-not-active")));
            }
            case "top" -> {
                if (perm(p, "headcount.top")) top(p, args.length > 1 && args[1].equalsIgnoreCase("last"));
            }
            case "history" -> {
                if (perm(p, "headcount.history")) HistoryGui.open(plugin, p, p.getUniqueId(), p.getName());
            }
            case "sidebar" -> {
                if (!perm(p, "headcount.sidebar")) return true;
                if (!plugin.sidebar().serverEnabled()) {
                    plugin.lang().send(p, "sidebar.disabled");
                    return true;
                }
                plugin.lang().send(p, plugin.sidebar().toggle(p) ? "sidebar.toggled-on" : "sidebar.toggled-off");
            }
            case "effects" -> {
                if (!perm(p, "headcount.effects")) return true;
                var list = plugin.actions().describe(p.getUniqueId());
                if (list.isEmpty()) {
                    plugin.lang().send(p, "effects.none");
                    return true;
                }
                plugin.lang().send(p, "effects.header", Vars.of("count", list.size()));
                for (Vars v : list) plugin.lang().send(p, "effects.line", v);
            }
            default -> plugin.lang().send(p, "general.unknown-command", Vars.of("label", label));
        }
        return true;
    }

    private static int parsePage(String s) {
        try {
            return Math.max(0, Integer.parseInt(s) - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void help(CommandSender sender, String label) {
        plugin.lang().send(sender, "help.header");
        for (String[] e : PLAYER) {
            if (!sender.hasPermission(e[1])) continue;
            plugin.lang().send(sender, "help.player." + (e[0].isEmpty() ? "vault" : e[0]), Vars.of("label", label));
        }
        if (sender.hasPermission("headcount.trade")) plugin.lang().send(sender, "help.player.trade");
        if (sender.hasPermission("headcount.admin")) plugin.lang().send(sender, "help.admin-hint", Vars.of("label", label));
    }

    private void top(Player p, boolean last) {
        int n = Math.max(1, plugin.configs().main().getInt("top.lines", 10));
        if (!last) {
            if (plugin.cycles().current() == null) {
                plugin.lang().send(p, "cycle.not-started");
                return;
            }
            List<LiveStats.Entry> top = plugin.stats().top();
            plugin.lang().send(p, "top.header", Vars.of("cycle", plugin.cycles().currentId(), "count", Math.min(n, top.size())));
            if (top.isEmpty()) plugin.lang().send(p, "top.empty");
            for (int i = 0; i < Math.min(n, top.size()); i++) {
                LiveStats.Entry e = top.get(i);
                plugin.lang().send(p, "top.line", Vars.of("rank", i + 1, "name", e.name(), "unique", e.unique(), "total", e.total()));
            }
            return;
        }
        int lastCycle = plugin.cycles().currentId() - 1;
        plugin.db().query(c -> {
            List<Vars> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT name, unique_heads, score FROM results WHERE cycle=? AND active=1 ORDER BY unique_heads DESC, name LIMIT ?")) {
                ps.setInt(1, lastCycle);
                ps.setInt(2, n);
                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) out.add(Vars.of("rank", rank++, "name", rs.getString(1), "unique", rs.getInt(2), "score", rs.getInt(3)));
                }
            }
            return out;
        }).thenAccept(rows -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.lang().send(p, "top.header-last", Vars.of("cycle", lastCycle));
            if (rows.isEmpty()) plugin.lang().send(p, "top.empty");
            for (Vars v : rows) plugin.lang().send(p, "top.line-last", v);
        }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String[] e : PLAYER) if (!e[0].isEmpty() && sender.hasPermission(e[1])) out.add(e[0]);
            if (sender.hasPermission("headcount.admin")) out.add("admin");
            return filter(out, args[0]);
        }
        if (args[0].equalsIgnoreCase("admin")) return admin.onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) return filter(List.of("last"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("shop")) return filter(plugin.shop().tiers().stream().map(t -> t.id()).toList(), args[1]);
        return List.of();
    }

    static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
