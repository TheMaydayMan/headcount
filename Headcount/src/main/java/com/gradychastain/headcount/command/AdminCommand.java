package com.gradychastain.headcount.command;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.cycle.CycleResults;
import com.gradychastain.headcount.cycle.PlayerResult;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.history.HistoryGui;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Durations;
import com.gradychastain.headcount.util.Formats;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /hcadmin (/hca), also reachable as /hc admin. */
public final class AdminCommand implements TabExecutor {
    private static final List<String[]> SUBS = List.of(
            new String[]{"cycle", "headcount.admin.cycle"}, new String[]{"vault", "headcount.admin.vault"},
            new String[]{"giveheads", "headcount.admin.heads"}, new String[]{"points", "headcount.admin.points"},
            new String[]{"title", "headcount.admin.titles"}, new String[]{"history", "headcount.admin.history"},
            new String[]{"effects", "headcount.admin.effects"}, new String[]{"reset", "headcount.admin.reset"},
            new String[]{"trade", "headcount.admin.trade"}, new String[]{"audit", "headcount.admin.audit"},
            new String[]{"reload", "headcount.admin.reload"}, new String[]{"status", "headcount.admin.debug"},
            new String[]{"flag", "headcount.admin.debug"}, new String[]{"inspect", "headcount.admin.debug"},
            new String[]{"placeholders", "headcount.admin.debug"}, new String[]{"integrations", "headcount.admin.debug"},
            new String[]{"selftest", "headcount.admin.debug"},
            new String[]{"run", "headcount.admin.run"}, new String[]{"hook", "headcount.admin.run"},
            new String[]{"studio", "headcount.admin.studio"});

    private final HeadcountPlugin plugin;

    public AdminCommand(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private String actor(CommandSender s) {
        return s instanceof Player p ? p.getName() + "(" + p.getUniqueId() + ")" : "console";
    }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node)) return true;
        plugin.lang().send(s, "general.no-permission");
        return false;
    }

    private UUID target(CommandSender s, String name) {
        UUID id = plugin.players().resolve(name);
        if (id == null) plugin.lang().send(s, "admin.unknown-player", "player", name);
        return id;
    }

    private void usage(CommandSender s, String key) {
        plugin.lang().send(s, "admin.usage." + key);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!perm(sender, "headcount.admin")) return true;
        if (!plugin.isLoaded()) {
            plugin.lang().send(sender, "general.loading");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.lang().send(sender, "admin.help.header");
            for (String[] s : SUBS) if (sender.hasPermission(s[1])) plugin.lang().send(sender, "admin.help." + s[0]);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] a = Arrays.copyOfRange(args, 1, args.length);
        for (String[] s : SUBS) if (s[0].equals(sub) && !perm(sender, s[1])) return true;
        try {
            switch (sub) {
                case "cycle" -> cycle(sender, a);
                case "vault" -> vault(sender, a);
                case "giveheads" -> giveHeads(sender, a);
                case "points" -> points(sender, a);
                case "title" -> title(sender, a);
                case "history" -> history(sender, a);
                case "effects" -> effects(sender, a);
                case "reset" -> reset(sender, a);
                case "trade" -> trade(sender, a);
                case "audit" -> audit(sender, a);
                case "reload" -> {
                    plugin.reloadAll();
                    plugin.audit().log(AuditType.ADMIN, "action", "reload", "actor", actor(sender));
                    plugin.lang().send(sender, "admin.reloaded");
                }
                case "status" -> status(sender, a);
                case "flag" -> flag(sender, a);
                case "inspect" -> inspect(sender);
                case "placeholders" -> placeholders(sender, a);
                case "integrations" -> {
                    plugin.lang().send(sender, "admin.integrations.header");
                    for (Map.Entry<String, String> e : plugin.integrations().summary().entrySet()) {
                        plugin.lang().send(sender, "admin.integrations.line", "category", e.getKey(), "provider", e.getValue());
                    }
                }
                case "selftest" -> new SelfTest(plugin).run(sender);
                case "run" -> run(sender, a);
                case "hook" -> hook(sender, a);
                case "studio" -> studio(sender, a);
                default -> plugin.lang().send(sender, "admin.unknown-subcommand", "label", label);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Admin command failed", e);
            plugin.lang().send(sender, "admin.error", "error", String.valueOf(e.getMessage()));
        }
        return true;
    }

    // ---------------------------------------------------------------- studio

    private void studio(CommandSender s, String[] a) {
        var studio = plugin.studio();
        if (a.length > 0 && a[0].equalsIgnoreCase("reset")) {
            String pw = studio.auth().newPassword();
            plugin.audit().log(AuditType.ADMIN, "action", "studio-new-password", "actor", actor(s));
            plugin.lang().sendTemplate(s, plugin.lang().fill("admin.studio.reset", "password", pw), null);
            return;
        }
        if (a.length > 0) {
            usage(s, "studio");
            return;
        }
        if (!studio.isRunning()) {
            if (studio.problem() == null) plugin.lang().send(s, "admin.studio.off");
            else plugin.lang().send(s, "admin.studio.failed", "error", studio.problem());
            return;
        }
        plugin.lang().sendTemplate(s, plugin.lang().fill("admin.studio.link", "url", studio.link(s)), null);
        plugin.lang().sendTemplate(s, plugin.lang().fill("admin.studio.password", "password", studio.auth().password()), null);
        plugin.lang().send(s, "admin.studio.signed-in", "count", studio.auth().signedIn());
    }

    // ---------------------------------------------------------------- cycle

    private void cycle(CommandSender s, String[] a) {
        String op = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        Cycle c = plugin.cycles().current();
        switch (op) {
            case "info" -> {
                if (c == null) {
                    long first = plugin.cycles().pendingFirstStart();
                    plugin.lang().send(s, "admin.cycle.none", "starts", first < 0 ? "-" : Formats.date(first),
                            "in", first < 0 ? "-" : Formats.duration(first - System.currentTimeMillis()));
                    return;
                }
                plugin.lang().send(s, "admin.cycle.info", Vars.of("cycle", c.id(), "start", Formats.date(c.startMs()), "end", Formats.date(c.endMs()),
                        "time_left", Formats.duration(c.remainingMs()), "length", c.lengthDays(), "vault_size", c.vaultSize(),
                        "starting_heads", c.startingHeads(), "active", plugin.stats().activeCount(), "players", plugin.stats().totalPlayers(),
                        "zone", plugin.cycles().schedule().zone().getId(), "run", c.token() == null ? "-" : c.token()));
            }
            case "end" -> {
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
                    plugin.lang().send(s, "admin.cycle.end-confirm");
                    return;
                }
                plugin.lang().send(s, plugin.cycles().forceEnd(actor(s)) ? "admin.cycle.ended" : "admin.cycle.cannot");
            }
            case "start" -> {
                if (c != null) {
                    plugin.lang().send(s, "admin.cycle.already-running");
                    return;
                }
                plugin.lang().send(s, plugin.cycles().forceStart(actor(s)) ? "admin.cycle.started" : "admin.cycle.cannot");
            }
            case "simulate" -> {
                CycleResults r = plugin.cycles().simulate();
                if (r == null) {
                    plugin.lang().send(s, "cycle.not-started");
                    return;
                }
                plugin.lang().send(s, "admin.cycle.simulate-header", Vars.of("cycle", r.cycle().id(), "active", r.active(),
                        "average", String.format(Locale.ROOT, "%.2f", r.averageScore()), "rewarded", r.rewarded(), "punished", r.punished()));
                int shown = 0;
                for (PlayerResult pr : r.ranked()) {
                    if (shown++ >= 15) break;
                    List<String> rules = plugin.outcomes().select(pr, r.cycle()).stream().map(x -> x.id()).toList();
                    plugin.lang().send(s, "admin.cycle.simulate-line", Vars.of("rank", pr.rank(), "name", pr.name(), "unique", pr.unique(),
                            "score", pr.score(), "points", pr.pointsAfter(), "outcomes", rules.isEmpty() ? "-" : String.join(", ", rules)));
                }
            }
            case "setend" -> {
                if (a.length < 2) {
                    usage(s, "cycle-setend");
                    return;
                }
                try {
                    long ms = LocalDateTime.parse(a[1]).atZone(plugin.cycles().schedule().zone()).toInstant().toEpochMilli();
                    plugin.lang().send(s, plugin.cycles().setEnd(ms, actor(s)) ? "admin.cycle.end-changed" : "admin.cycle.cannot", "end", Formats.date(ms));
                } catch (DateTimeParseException e) {
                    usage(s, "cycle-setend");
                }
            }
            case "extend" -> {
                if (c == null || a.length < 2) {
                    usage(s, "cycle-extend");
                    return;
                }
                boolean minus = a[1].startsWith("-");
                long ms = Durations.parseMillis(minus ? a[1].substring(1) : a[1]);
                if (ms < 0) {
                    usage(s, "cycle-extend");
                    return;
                }
                long end = c.endMs() + (minus ? -ms : ms);
                plugin.lang().send(s, plugin.cycles().setEnd(end, actor(s)) ? "admin.cycle.end-changed" : "admin.cycle.cannot", "end", Formats.date(end));
            }
            default -> usage(s, "cycle");
        }
    }

    // ---------------------------------------------------------------- vault, heads, points, titles

    private void vault(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.lang().send(s, "general.players-only");
            return;
        }
        if (a.length < 1) {
            usage(s, "vault");
            return;
        }
        UUID id = target(s, a[0]);
        if (id == null) return;
        boolean edit = p.hasPermission("headcount.admin.vault.edit") && !(a.length > 1 && a[1].equalsIgnoreCase("view"));
        plugin.vaults().open(p, id, 0, true, edit);
        plugin.audit().log(AuditType.ADMIN, "action", edit ? "vault-edit-open" : "vault-view", "vault", id, "actor", actor(s));
    }

    private void giveHeads(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "giveheads");
            return;
        }
        UUID to = target(s, a[0]);
        if (to == null) return;
        int amount;
        try {
            amount = Math.max(1, Math.min(1000, Integer.parseInt(a[1])));
        } catch (NumberFormatException e) {
            usage(s, "giveheads");
            return;
        }
        UUID owner = a.length > 2 && !a[2].equalsIgnoreCase("self") ? target(s, a[2]) : to;
        if (owner == null) return;
        boolean next = a.length > 3 && a[3].equalsIgnoreCase("next");
        boolean vault = a.length > 4 && a[4].equalsIgnoreCase("vault");
        String ownerName = plugin.players().name(owner);
        plugin.audit().log(AuditType.ADMIN, "action", "give-heads", "to", to, "owner", owner, "amount", amount, "next-cycle", next, "to-vault", vault, "actor", actor(s));
        if (next) {
            plugin.vaults().queueNextCycleHeads(to, owner, ownerName, amount, "admin");
            plugin.lang().send(s, "admin.heads.queued", "amount", amount, "owner", ownerName, "player", plugin.players().name(to));
            return;
        }
        Cycle c = plugin.cycles().current();
        if (c == null) {
            plugin.lang().send(s, "cycle.not-started");
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        int inVault = 0;
        for (int i = 0; i < amount; i++) {
            HeadData h = plugin.heads().newHead(owner, ownerName, c.id(), "admin", to, actor(s));
            ItemStack item = plugin.heads().createItem(h);
            if (vault && plugin.vaults().addHead(to, h, item, actor(s))) inVault++;
            else items.add(item);
        }
        plugin.deliveries().give(to, items, "admin-give");
        plugin.lang().send(s, "admin.heads.given", "amount", amount, "owner", ownerName, "player", plugin.players().name(to), "in_vault", inVault);
    }

    private void points(CommandSender s, String[] a) {
        if (a.length < 3) {
            usage(s, "points");
            return;
        }
        UUID id = target(s, a[0]);
        if (id == null) return;
        long amount;
        try {
            amount = Long.parseLong(a[2]);
        } catch (NumberFormatException e) {
            usage(s, "points");
            return;
        }
        boolean next = a.length > 3 && a[3].equalsIgnoreCase("next");
        String op = a[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "give", "add" -> plugin.points().change(id, Math.abs(amount), next, "admin", actor(s));
            case "take", "remove" -> plugin.points().change(id, -Math.abs(amount), next, "admin", actor(s));
            case "set" -> plugin.points().set(id, amount, actor(s));
            default -> {
                usage(s, "points");
                return;
            }
        }
        plugin.audit().log(AuditType.ADMIN, "action", "points-" + op, "player", id, "amount", amount, "next-cycle", next, "actor", actor(s));
        plugin.lang().send(s, "admin.points.done", "player", plugin.players().name(id), "points", plugin.points().balance(id), "points_next", plugin.points().pending(id));
    }

    private void title(CommandSender s, String[] a) {
        if (a.length < 3) {
            usage(s, "title");
            return;
        }
        UUID id = target(s, a[0]);
        if (id == null) return;
        String t = a[2].toLowerCase(Locale.ROOT);
        if (plugin.titles().title(t) == null) {
            plugin.lang().send(s, "admin.title.unknown", "title", t);
            return;
        }
        boolean grant = a[1].equalsIgnoreCase("grant") || a[1].equalsIgnoreCase("give");
        if (grant) plugin.titles().grant(id, t, "admin:" + actor(s));
        else plugin.titles().revoke(id, t, "admin:" + actor(s));
        plugin.audit().log(AuditType.ADMIN, "action", grant ? "title-grant" : "title-revoke", "player", id, "title", t, "actor", actor(s));
        plugin.lang().send(s, grant ? "admin.title.granted" : "admin.title.revoked", "title", t, "player", plugin.players().name(id));
    }

    private void history(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.lang().send(s, "general.players-only");
            return;
        }
        if (a.length < 1) {
            usage(s, "history");
            return;
        }
        UUID id = target(s, a[0]);
        if (id != null) HistoryGui.open(plugin, p, id, plugin.players().name(id));
    }

    private void effects(CommandSender s, String[] a) {
        if (a.length < 1) {
            usage(s, "effects");
            return;
        }
        UUID id = target(s, a[0]);
        if (id == null) return;
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (op.equals("remove")) {
            if (a.length < 3) {
                usage(s, "effects");
                return;
            }
            if (a[2].equalsIgnoreCase("all")) {
                plugin.lang().send(s, "admin.effects.removed-all", "count", plugin.actions().removeAll(id, actor(s)), "player", plugin.players().name(id));
            } else {
                plugin.lang().send(s, plugin.actions().remove(a[2], id, actor(s)) ? "admin.effects.removed" : "admin.effects.not-found", "id", a[2]);
            }
            return;
        }
        var list = plugin.actions().describe(id);
        plugin.lang().send(s, "admin.effects.header", "player", plugin.players().name(id), "count", list.size());
        for (Vars v : list) plugin.lang().send(s, "admin.effects.line", v);
    }

    private static final List<String> RESET_PARTS = List.of("vault", "points", "titles", "streak", "effects", "history", "all");

    /** "cycle" or "hard" as the first word means the whole-server reset, unless a player of that name is being reset. */
    private static boolean serverReset(String[] a, String word) {
        return a.length >= 1 && a[0].equalsIgnoreCase(word) && (a.length == 1 || !RESET_PARTS.contains(a[1].toLowerCase(Locale.ROOT)));
    }

    private void resetCycle(CommandSender s, String[] a) {
        if (!perm(s, "headcount.admin.reset.cycle")) return;
        Cycle c = plugin.cycles().current();
        if (c == null) {
            plugin.lang().send(s, "cycle.not-started");
            return;
        }
        boolean delete = plugin.resets().deleteHeads(a);
        if (Arrays.stream(a).noneMatch(x -> x.equalsIgnoreCase("confirm"))) {
            plugin.lang().send(s, "admin.reset.cycle-confirm", Vars.of("cycle", c.id(), "args", delete ? "delete-heads" : "keep-heads")
                    .mini("old_heads", plugin.lang().raw(delete ? "admin.reset.old-heads-delete" : "admin.reset.old-heads-keep")));
            return;
        }
        if (!plugin.cycles().restart(actor(s), delete)) {
            plugin.lang().send(s, "admin.cycle.cannot");
            return;
        }
        Cycle now = plugin.cycles().current();
        plugin.lang().send(s, "admin.reset.cycle-done", "cycle", now.id(), "end", Formats.date(now.endMs()));
        for (Player p : plugin.getServer().getOnlinePlayers()) plugin.lang().send(p, "admin.reset.cycle-broadcast", "cycle", now.id());
    }

    private void reset(CommandSender s, String[] a) {
        if (serverReset(a, "cycle")) {
            resetCycle(s, a);
            return;
        }
        if (serverReset(a, "hard")) {
            String code = a.length > 1 && a[1].chars().allMatch(Character::isDigit) ? a[1] : null;
            plugin.resets().hardCommand(s, code, plugin.resets().deleteHeads(a), actor(s));
            return;
        }
        if (a.length < 2) {
            usage(s, "reset");
            return;
        }
        UUID id = target(s, a[0]);
        if (id == null) return;
        String what = a[1].toLowerCase(Locale.ROOT);
        if (a.length < 3 || !a[2].equalsIgnoreCase("confirm")) {
            plugin.lang().send(s, "admin.reset.confirm", "player", plugin.players().name(id), "what", what);
            return;
        }
        List<String> done = new ArrayList<>();
        boolean all = what.equals("all");
        PlayerData d = plugin.players().loaded(id);
        if (all || what.equals("vault")) {
            plugin.vaults().clear(id, actor(s));
            done.add("vault");
        }
        if (all || what.equals("points")) {
            plugin.points().set(id, 0, actor(s));
            plugin.points().change(id, -plugin.points().pending(id), true, "reset", actor(s));
            done.add("points");
        }
        if (all || what.equals("titles")) {
            for (String t : new ArrayList<>(plugin.titles().owned(id))) plugin.titles().revoke(id, t, "reset");
            done.add("titles");
        }
        if (all || what.equals("streak")) {
            if (d != null) d.streak = 0;
            plugin.playerStore().set(id, "streak", 0);
            done.add("streak");
        }
        if (all || what.equals("effects")) {
            plugin.actions().removeAll(id, actor(s));
            done.add("effects");
        }
        if (all || what.equals("history")) {
            plugin.db().write(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM results WHERE uuid=?")) {
                    ps.setString(1, id.toString());
                    ps.executeUpdate();
                }
            });
            if (d != null) {
                d.lastScore = null;
                d.lastUnique = null;
            }
            plugin.playerStore().set(id, "last_score", null);
            plugin.playerStore().set(id, "last_unique", null);
            done.add("history");
        }
        if (done.isEmpty()) {
            usage(s, "reset");
            return;
        }
        plugin.audit().log(AuditType.ADMIN, "action", "reset", "player", id, "what", String.join(",", done), "actor", actor(s));
        plugin.lang().send(s, "admin.reset.done", "player", plugin.players().name(id), "what", String.join(", ", done));
    }

    private void trade(CommandSender s, String[] a) {
        if (a.length < 2 || !a[0].equalsIgnoreCase("cancel")) {
            usage(s, "trade");
            return;
        }
        UUID id = target(s, a[1]);
        if (id == null) return;
        plugin.lang().send(s, plugin.trades().adminCancel(id, actor(s)) ? "admin.trade.cancelled" : "admin.trade.none", "player", plugin.players().name(id));
    }

    private void audit(CommandSender s, String[] a) {
        if (a.length < 1) {
            usage(s, "audit");
            return;
        }
        List<String> terms = new ArrayList<>();
        AuditType type = null;
        int page = 1;
        for (String arg : a) {
            String l = arg.toLowerCase(Locale.ROOT);
            if (l.startsWith("type:")) {
                try {
                    type = AuditType.valueOf(arg.substring(5).toUpperCase(Locale.ROOT).replace('-', '_'));
                } catch (IllegalArgumentException e) {
                    plugin.lang().send(s, "admin.audit.bad-type", "types", String.join(", ", Arrays.stream(AuditType.values()).map(Enum::name).toList()));
                    return;
                }
            } else if (l.startsWith("page:")) {
                try {
                    page = Math.max(1, Integer.parseInt(l.substring(5)));
                } catch (NumberFormatException ignored) {
                    // page 1
                }
            } else {
                // A player name searches by their UUID too, so renamed players are still found.
                UUID id = plugin.players().resolve(arg);
                terms.add(id != null && !arg.contains("-") ? id.toString() : arg);
            }
        }
        int perPage = Math.max(1, plugin.configs().main().getInt("audit.search-page-size", 10));
        int finalPage = page;
        AuditType finalType = type;
        plugin.lang().send(s, "admin.audit.searching");
        plugin.audit().search(terms, type, perPage * page).thenAccept(lines -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            int from = (finalPage - 1) * perPage;
            if (lines.size() <= from) {
                plugin.lang().send(s, "admin.audit.none");
                return;
            }
            plugin.lang().send(s, "admin.audit.header", "terms", String.join(" ", terms), "page", finalPage,
                    "type", finalType == null ? "any" : finalType.name());
            for (int i = from; i < Math.min(lines.size(), from + perPage); i++) {
                plugin.lang().send(s, "admin.audit.line", "line", lines.get(i).replace('_', ' '));
            }
            if (lines.size() >= perPage * finalPage) plugin.lang().send(s, "admin.audit.more", "next", finalPage + 1);
        }));
    }

    // ---------------------------------------------------------------- debug

    private void status(CommandSender s, String[] a) {
        if (a.length < 1) {
            usage(s, "status");
            return;
        }
        Player p = plugin.getServer().getPlayerExact(a[0]);
        if (p == null) {
            plugin.lang().send(s, "admin.player-offline", "player", a[0]);
            return;
        }
        if (a.length > 1) {
            plugin.lang().send(s, "admin.status.one", "player", p.getName(), "status", a[1], "value", plugin.statuses().format(plugin.statuses().has(p, a[1])));
            return;
        }
        plugin.lang().send(s, "admin.status.header", "player", p.getName());
        for (String n : plugin.statuses().names()) {
            plugin.lang().send(s, "admin.status.line", "status", n, "value", plugin.statuses().format(plugin.statuses().has(p, n)),
                    "source", plugin.statuses().definition(n).source());
        }
        for (Map.Entry<String, String> e : plugin.statuses().errors().entrySet()) plugin.lang().send(s, "admin.status.error", "status", e.getKey(), "error", e.getValue());
    }

    private void flag(CommandSender s, String[] a) {
        if (a.length < 3) {
            usage(s, "flag");
            return;
        }
        UUID id = target(s, a[0]);
        if (id == null) return;
        boolean set = a[1].equalsIgnoreCase("set");
        plugin.players().setFlag(id, a[2], set);
        plugin.audit().log(AuditType.ADMIN, "action", set ? "flag-set" : "flag-clear", "player", id, "flag", a[2], "actor", actor(s));
        plugin.lang().send(s, set ? "admin.flag.set" : "admin.flag.cleared", "flag", a[2], "player", plugin.players().name(id));
    }

    private void inspect(CommandSender s) {
        if (!(s instanceof Player p)) {
            plugin.lang().send(s, "general.players-only");
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        HeadData d = plugin.heads().read(item);
        if (d == null) {
            plugin.lang().send(s, "admin.inspect.not-economy");
            return;
        }
        UUID inVault = plugin.vaults().vaultHolding(d.id());
        plugin.lang().send(s, "admin.inspect.head", Vars.of("head", d.id(), "owner", d.ownerName(), "owner_uuid", d.owner(), "cycle", d.cycle(),
                "current", plugin.lang().plain(plugin.heads().isCurrent(d) ? "admin.inspect.current" : plugin.heads().isVoid(d) ? "admin.inspect.defunct" : "admin.inspect.expired"),
                "run", d.token() == null ? "-" : d.token(), "source", d.source(), "created", Formats.date(d.created()),
                "in_vault", inVault == null ? plugin.lang().plain("admin.inspect.not-in-vault") : plugin.players().name(inVault), "amount", item.getAmount()));
    }

    private void placeholders(CommandSender s, String[] a) {
        Player p = a.length > 0 ? plugin.getServer().getPlayerExact(a[0]) : s instanceof Player pl ? pl : null;
        if (p == null) {
            usage(s, "placeholders");
            return;
        }
        plugin.lang().send(s, "admin.placeholders.header", "player", p.getName());
        for (Map.Entry<String, String> e : plugin.placeholders().dump(p).entrySet()) {
            plugin.lang().send(s, "admin.placeholders.line", "name", e.getKey(), "value", e.getValue());
        }
    }

    private void run(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "run");
            return;
        }
        UUID id = target(s, a[0]);
        if (id == null) return;
        var list = plugin.actions().named(a[1]);
        if (list == null) {
            plugin.lang().send(s, "admin.run.unknown", "list", a[1]);
            return;
        }
        plugin.actions().run(list, id, Vars.of("player", plugin.players().name(id)), "admin:" + a[1]);
        plugin.audit().log(AuditType.ADMIN, "action", "run-list", "player", id, "list", a[1], "actor", actor(s));
        plugin.lang().send(s, "admin.run.done", "list", a[1], "player", plugin.players().name(id));
    }

    private void hook(CommandSender s, String[] a) {
        if (a.length < 1) {
            usage(s, "hook");
            return;
        }
        Player target = a.length > 1 ? plugin.getServer().getPlayerExact(a[1]) : s instanceof Player p ? p : null;
        boolean ok = plugin.hooks().runByName(a[0], target);
        plugin.audit().log(AuditType.ADMIN, "action", "run-hook", "hook", a[0], "player", target == null ? null : target.getName(), "actor", actor(s));
        plugin.lang().send(s, ok ? "admin.hook.done" : "admin.hook.unknown", "hook", a[0]);
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("headcount.admin")) return List.of();
        if (args.length <= 1) {
            List<String> out = new ArrayList<>(List.of("help"));
            for (String[] s : SUBS) if (sender.hasPermission(s[1])) out.add(s[0]);
            return HeadcountCommand.filter(out, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String last = args[args.length - 1];
        int i = args.length - 1;
        return switch (sub) {
            case "cycle" -> i == 1 ? HeadcountCommand.filter(List.of("info", "end", "start", "simulate", "setend", "extend"), last)
                    : i == 2 && args[1].equalsIgnoreCase("end") ? List.of("confirm") : List.of();
            case "vault", "history", "status", "placeholders" -> i == 1 ? plugin.players().complete(last, !sub.equals("status") && !sub.equals("placeholders"))
                    : sub.equals("vault") && i == 2 ? HeadcountCommand.filter(List.of("view", "edit"), last)
                    : sub.equals("status") && i == 2 ? HeadcountCommand.filter(new ArrayList<>(plugin.statuses().names()), last) : List.of();
            case "giveheads" -> switch (i) {
                case 1, 3 -> plugin.players().complete(last, true);
                case 2 -> List.of("1", "5", "27");
                case 4 -> HeadcountCommand.filter(List.of("current", "next"), last);
                case 5 -> HeadcountCommand.filter(List.of("inventory", "vault"), last);
                default -> List.of();
            };
            case "points" -> switch (i) {
                case 1 -> plugin.players().complete(last, true);
                case 2 -> HeadcountCommand.filter(List.of("give", "take", "set"), last);
                case 4 -> HeadcountCommand.filter(List.of("next"), last);
                default -> List.of();
            };
            case "title" -> switch (i) {
                case 1 -> plugin.players().complete(last, true);
                case 2 -> HeadcountCommand.filter(List.of("grant", "revoke"), last);
                case 3 -> HeadcountCommand.filter(new ArrayList<>(plugin.titles().all().keySet()), last);
                default -> List.of();
            };
            case "effects" -> switch (i) {
                case 1 -> plugin.players().complete(last, true);
                case 2 -> HeadcountCommand.filter(List.of("list", "remove"), last);
                case 3 -> HeadcountCommand.filter(List.of("all"), last);
                default -> List.of();
            };
            case "reset" -> {
                if (i == 1) {
                    List<String> out = new ArrayList<>();
                    if (sender.hasPermission("headcount.admin.reset.cycle")) out.add("cycle");
                    if (com.gradychastain.headcount.reset.ResetManager.mayHardReset(sender)) out.add("hard");
                    out.addAll(plugin.players().complete(last, true));
                    yield HeadcountCommand.filter(out, last);
                }
                if (args[1].equalsIgnoreCase("cycle") && i <= 3) yield HeadcountCommand.filter(List.of("delete-heads", "keep-heads", "confirm"), last);
                if (args[1].equalsIgnoreCase("hard") && i == 2) yield HeadcountCommand.filter(List.of("delete-heads", "keep-heads"), last);
                yield switch (i) {
                    case 2 -> HeadcountCommand.filter(RESET_PARTS, last);
                    case 3 -> List.of("confirm");
                    default -> List.of();
                };
            }
            case "trade" -> i == 1 ? HeadcountCommand.filter(List.of("cancel"), last) : i == 2 ? plugin.players().complete(last, false) : List.of();
            case "flag" -> i == 1 ? plugin.players().complete(last, true) : i == 2 ? HeadcountCommand.filter(List.of("set", "clear"), last) : List.of();
            case "studio" -> i == 1 ? HeadcountCommand.filter(List.of("reset"), last) : List.of();
            case "run" -> i == 1 ? plugin.players().complete(last, true) : i == 2 ? HeadcountCommand.filter(new ArrayList<>(plugin.actions().namedLists()), last) : List.of();
            case "hook" -> {
                if (i == 1) {
                    List<String> hooks = new ArrayList<>(List.of("cycle-start", "first-join", "first-join-cycle", "became-active", "join", "hurt-pet", "kill-pet", "open-trapped-chest"));
                    for (String w : plugin.configs().main().getSection("hooks.warnings").keys()) hooks.add("warning:" + w);
                    yield HeadcountCommand.filter(hooks, last);
                }
                yield i == 2 ? plugin.players().complete(last, false) : List.of();
            }
            case "audit" -> HeadcountCommand.filter(new ArrayList<>(Arrays.stream(AuditType.values()).map(t -> "type:" + t.name().toLowerCase(Locale.ROOT)).toList()), last);
            default -> List.of();
        };
    }
}
