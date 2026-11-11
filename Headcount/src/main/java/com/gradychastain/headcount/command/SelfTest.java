package com.gradychastain.headcount.command;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.item.ItemSpec;
import com.gradychastain.headcount.shop.ShopManager;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.title.TitleManager;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Items;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /hca selftest: checks, on the running server, everything that otherwise only shows up when a player opens a menu
 * or receives a message: economy head round-trips, every configured item and button, every shop entry in every
 * state, every message, title, sidebar line, the formula, statuses, and that every action list parsed fully.
 * Nothing is changed or recorded.
 */
public final class SelfTest {
    private final HeadcountPlugin plugin;
    private final List<String> failures = new ArrayList<>();
    private final Map<String, Integer> passed = new HashMap<>();

    public SelfTest(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private void pass(String group) {
        passed.merge(group, 1, Integer::sum);
    }

    private void fail(String group, String detail) {
        failures.add(group + ": " + detail);
    }

    private void check(String group, boolean ok, String detail) {
        if (ok) pass(group);
        else fail(group, detail);
    }

    private static Vars sample() {
        Vars v = Vars.of("player", "Steve", "owner", "Steve", "other", "Alex", "target", "Alex", "points", 12, "price", 5, "cycle", 3,
                "page", 1, "pages", 2, "unique", 4, "total", 9, "size", 27, "seconds", 3, "name", "Entry", "stock", 5, "missing", 2,
                "limit", 1, "bought", 0, "cooldown", "1m", "threshold", 10, "last_score", 4, "needed", 6, "my_heads", 1, "my_items", 2,
                "their_heads", 0, "their_items", 1, "max_heads", 24, "max_items", 24, "id", "x", "description", "d", "score", 2,
                "earned", 2, "spent", 1, "rank", 1, "streak", 1, "playtime", "1h", "kills", 0, "deaths", 0, "trades", 0, "ended", "-",
                "rolled_over", 0, "points_after", 3, "own", 1, "tier_number", 1, "tiers", 4, "tier_id", "common", "purchases_left", 1);
        v.mini("tier", "<white>Common").mini("title", "<gold>[Test]").mini("outcomes", "<gray>none").mini("target_tier", "<green>Next");
        return v;
    }

    private void buildItem(String group, String where, Object raw, Material fallback) {
        try {
            ItemStack item = ItemSpec.parse(raw, fallback).build(plugin, null, sample());
            check(group, !Items.isEmpty(item), where + " built an empty item");
        } catch (RuntimeException e) {
            fail(group, where + ": " + e.getMessage());
        }
    }

    private void layout(String group, Cfg cfg, int defaultRows, Map<String, List<String>> buttonStates) {
        MenuLayout l = new MenuLayout(cfg);
        int size = l.rows(defaultRows) * 9;
        if (cfg.isSection("filler")) buildItem(group, "filler", cfg.getSection("filler").raw(), Material.GRAY_STAINED_GLASS_PANE);
        Map<Integer, String> used = new HashMap<>();
        for (String id : cfg.getSection("buttons").keys()) {
            if (!l.hasButton(id)) continue;
            for (int s : l.slots(id)) {
                check(group, s >= 0 && s < size, "button " + id + " slot " + s + " is outside the " + size + "-slot menu");
                String prev = used.put(s, id);
                check(group, prev == null, "buttons " + prev + " and " + id + " share slot " + s);
            }
            List<String> states = buttonStates.getOrDefault(id, List.of());
            if (states.isEmpty()) {
                if (!id.equals("display")) buildItem(group, "button " + id, l.button(id).raw(), Material.STONE);
            } else {
                for (String st : states) buildItem(group, "button " + id + " (" + st + ")", l.button(id).merge(l.button(id).getSection(st)).raw(), Material.STONE);
            }
        }
    }

    /** Runs every check and logs only failures (startup and reload). */
    public void runQuiet() {
        runChecks();
        for (String f : failures) plugin.getLogger().warning("Config check: " + f);
        if (!failures.isEmpty()) plugin.getLogger().warning(failures.size() + " config check(s) failed. Run /hca selftest for the full report.");
    }

    private void runChecks() {
        heads();
        formulaAndStatuses();
        statusReferences();
        messages();
        menus();
        shop();
        titlesAndSidebar();
        lists();
    }

    void run(CommandSender to) {
        runChecks();
        int total = passed.values().stream().mapToInt(Integer::intValue).sum();
        plugin.lang().send(to, "admin.selftest.header");
        for (Map.Entry<String, Integer> e : passed.entrySet()) plugin.lang().send(to, "admin.selftest.group", "group", e.getKey(), "count", e.getValue());
        for (int i = 0; i < Math.min(40, failures.size()); i++) plugin.lang().send(to, "admin.selftest.failure", "detail", failures.get(i));
        plugin.lang().send(to, failures.isEmpty() ? "admin.selftest.ok" : "admin.selftest.failed", "passed", total, "failed", failures.size());
        plugin.getLogger().info("Self-test: " + total + " checks passed, " + failures.size() + " failed.");
        for (String f : failures) plugin.getLogger().warning("Self-test failure: " + f);
    }

    // ---------------------------------------------------------------- checks

    private void heads() {
        String g = "heads";
        Cycle c = plugin.cycles().current();
        if (c == null) {
            fail(g, "no cycle is running, so current-cycle heads cannot be checked");
            return;
        }
        HeadData d = new HeadData(UUID.randomUUID(), UUID.randomUUID(), "SelfTest", c.id(), System.currentTimeMillis(), "selftest", c.token());
        ItemStack item = plugin.heads().createItem(d);
        HeadData back = plugin.heads().read(item);
        check(g, back != null && back.id().equals(d.id()) && back.owner().equals(d.owner()) && back.cycle() == d.cycle()
                && back.source().equals(d.source()), "economy data did not read back from the item");
        check(g, item.getType() == Material.PLAYER_HEAD, "economy head is not a PLAYER_HEAD");
        ItemStack copy = Items.fromBytes(Items.toBytes(item));
        HeadData again = plugin.heads().read(copy);
        check(g, again != null && again.id().equals(d.id()), "economy data was lost in a byte round-trip (placed heads and vault storage use this)");
        check(g, !plugin.heads().refresh(item), "a freshly made head was re-rendered, so its render fingerprint isn't stable");
        check(g, plugin.heads().readCurrent(item) != null, "a current-cycle head is not seen as current");
        check(g, plugin.heads().read(new ItemStack(Material.PLAYER_HEAD)) == null, "a vanilla head was read as an economy head");
        ItemStack other = plugin.heads().createItem(new HeadData(UUID.randomUUID(), d.owner(), "SelfTest", c.id(), d.created(), "selftest", c.token()));
        check(g, !item.isSimilar(other), "two heads with different IDs would stack");
        int max = plugin.configs().main().getInt("heads.item.max-stack-size", 1);
        check(g, item.getMaxStackSize() == max, "max stack size is " + item.getMaxStackSize() + ", config says " + max);
        HeadData old = new HeadData(UUID.randomUUID(), d.owner(), "SelfTest", c.id() - 1, d.created(), "selftest", null);
        ItemStack expired = plugin.heads().createItem(old);
        HeadData otherRun = new HeadData(UUID.randomUUID(), d.owner(), "SelfTest", c.id(), d.created(), "selftest", "selftest-run");
        check(g, !plugin.heads().isCurrent(otherRun), "a head from another run of this cycle (before a reset) counts as current");
        check(g, plugin.heads().read(plugin.heads().createItem(otherRun)).token().equals("selftest-run"), "a head's cycle token did not read back from the item");
        check(g, plugin.heads().readCurrent(expired) == null && plugin.heads().read(expired) != null, "an older cycle's head isn't read as expired but still intact");
        String curName = item.getItemMeta().hasDisplayName() ? PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()) : "";
        String oldName = expired.getItemMeta().hasDisplayName() ? PlainTextComponentSerializer.plainText().serialize(expired.getItemMeta().displayName()) : "";
        check(g, !curName.equals(oldName), "expired and current heads render the same name");
    }

    private void formulaAndStatuses() {
        String g = "formula";
        Map<String, Double> vars = new HashMap<>();
        for (String v : com.gradychastain.headcount.scoring.ScoreService.VARIABLES.keySet()) vars.put(v, 3.0);
        vars.put("active", 12.0);
        try {
            double v = plugin.scores().evaluate(plugin.scores().formula(), vars, null);
            check(g, !Double.isNaN(v) && !Double.isInfinite(v), "the formula gives " + v + " for sample values");
            for (String var : plugin.scores().formula().variables()) {
                check(g, com.gradychastain.headcount.scoring.ScoreService.VARIABLES.containsKey(var), "unknown formula variable " + var);
            }
        } catch (RuntimeException e) {
            fail(g, "formula failed: " + e.getMessage());
        }
        for (Map.Entry<String, String> e : plugin.statuses().errors().entrySet()) fail("statuses", e.getKey() + ": " + e.getValue());
        for (String n : plugin.statuses().names()) pass("statuses");
    }

    /** Defined in statuses.yml, even if disabled (a disabled status is reported once, under "statuses"). */
    private boolean known(String status) {
        String n = status.trim().toLowerCase(java.util.Locale.ROOT);
        if (n.startsWith("!")) n = n.substring(1).trim();
        return plugin.statuses().exists(status) || plugin.statuses().errors().containsKey(n);
    }

    private void statusReferences() {
        String g = "status names";
        Map<String, List<String>> uses = new java.util.LinkedHashMap<>();
        uses.put("config.yml vault.block-movement.statuses", plugin.configs().main().getStringList("vault.block-movement.statuses"));
        uses.put("config.yml kills.killer-blocked-statuses", plugin.configs().main().getStringList("kills.killer-blocked-statuses"));
        Cfg trade = plugin.configs().trade();
        uses.put("trade.yml statuses.block-request", trade.getStringList("statuses.block-request"));
        uses.put("trade.yml statuses.block-accept", trade.getStringList("statuses.block-accept"));
        uses.put("trade.yml statuses.cancel-open", trade.getStringList("statuses.cancel-open"));
        String vanished = trade.getString("request.vanished-status", "");
        if (!vanished.isBlank()) uses.put("trade.yml request.vanished-status", List.of(vanished));
        Cfg defs = plugin.configs().statuses().getSection("statuses");
        for (String name : defs.keys()) {
            Cfg d = defs.getSection(name);
            String src = d.getString("source", "");
            if (src.equals("all-of") || src.equals("any-of")) uses.put("statuses.yml " + name, d.getStringList("statuses"));
            if (src.equals("not")) uses.put("statuses.yml " + name, List.of(d.getString("status", "")));
            if (d.has("fallback")) uses.put("statuses.yml " + name + " fallback", List.of(d.getString("fallback", "")));
            if (src.equals("nearby-player") && !d.getString("status", "").isBlank()) uses.put("statuses.yml " + name, List.of(d.getString("status", "")));
        }
        for (Map.Entry<String, List<String>> e : uses.entrySet()) {
            for (String s : e.getValue()) check(g, known(s), e.getKey() + " names unknown status '" + s + "'");
        }
        // Every gate status has a registered bypass permission (default false) reachable from headcount.bypass.*.
        var pm = plugin.getServer().getPluginManager();
        var root = pm.getPermission("headcount.bypass.*");
        Map<String, List<String>> gates = Map.of("vault-move", plugin.configs().main().getStringList("vault.block-movement.statuses"),
                "trade-send", trade.getStringList("statuses.block-request"), "trade-accept", trade.getStringList("statuses.block-accept"),
                "trade-cancel", trade.getStringList("statuses.cancel-open"));
        for (Map.Entry<String, List<String>> e : gates.entrySet()) {
            var wildcard = pm.getPermission("headcount.bypass." + e.getKey() + ".*");
            check("permissions", wildcard != null && root != null && root.getChildren().containsKey(wildcard.getName()),
                    "headcount.bypass." + e.getKey() + ".* is not registered under headcount.bypass.*");
            for (String st : e.getValue()) {
                String node = "headcount.bypass." + e.getKey() + "." + st.toLowerCase(java.util.Locale.ROOT).replaceFirst("^!", "not-");
                var perm = pm.getPermission(node);
                check("permissions", perm != null && perm.getDefault() == org.bukkit.permissions.PermissionDefault.FALSE
                        && wildcard != null && wildcard.getChildren().containsKey(node), node + " is not registered correctly");
            }
        }
    }

    private void messages() {
        String g = "messages";
        File file = new File(plugin.getDataFolder(), "lang/" + plugin.lang().language() + ".yml");
        if (!file.exists()) file = new File(plugin.getDataFolder(), "lang/en_US.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(true)) {
            if (y.isConfigurationSection(key) || key.equals("config-version")) continue;
            Object raw = y.get(key);
            String text = raw instanceof List<?> l ? String.join("\n", l.stream().map(String::valueOf).toList()) : String.valueOf(raw);
            try {
                plugin.text().mini().deserialize(text, Placeholder.parsed("prefix", "x"), sample().resolver());
                pass(g);
            } catch (RuntimeException e) {
                fail(g, key + ": " + e.getMessage());
            }
        }
    }

    private void menus() {
        layout("vault menu", plugin.configs().main().getSection("vault.gui"), 6, Map.of());
        if (plugin.configs().main().isSection("vault.gui.locked-slot")) {
            buildItem("vault menu", "locked-slot", plugin.configs().main().getSection("vault.gui.locked-slot").raw(), Material.BLACK_STAINED_GLASS_PANE);
        }
        Cfg trade = plugin.configs().trade().getSection("gui");
        layout("trade menu", trade, 6, Map.of("confirm", List.of("unconfirmed", "confirmed"), "other-status", List.of("unconfirmed", "confirmed"),
                "countdown", List.of("idle", "running")));
        int tradeSize = new MenuLayout(trade).rows(6) * 9;
        List<Integer> own = trade.getIntList("own-slots"), other = trade.getIntList("other-slots");
        check("trade menu", own.size() == other.size() && !own.isEmpty(), "own-slots and other-slots must be the same length and not empty");
        Set<Integer> all = new HashSet<>();
        for (int s : own) check("trade menu", all.add(s) && s < tradeSize, "own slot " + s + " repeats or is outside the menu");
        for (int s : other) check("trade menu", all.add(s) && s < tradeSize, "other slot " + s + " overlaps or is outside the menu");
        MenuLayout tl = new MenuLayout(trade);
        for (String id : trade.getSection("buttons").keys()) {
            for (int s : tl.slots(id)) check("trade menu", !all.contains(s), "button " + id + " sits on an offer slot (" + s + ")");
        }
        layout("shop menu", plugin.configs().shop().getSection("gui"), 6, Map.of());
        layout("confirm menu", plugin.configs().shop().getSection("confirm-gui"), 3, Map.of());
        layout("titles menu", plugin.configs().titles().getSection("gui"), 6, Map.of());
        Cfg history = plugin.configs().main().getSection("history.gui");
        layout("history menu", history, 6, Map.of());
        Cfg entry = history.getSection("entry");
        for (String st : List.of("positive", "negative", "zero", "inactive")) buildItem("history menu", "entry (" + st + ")", entry.merge(entry.getSection(st)).raw(), Material.PAPER);
    }

    private void shop() {
        String g = "shop";
        MenuLayout l = plugin.shop().layout();
        int size = l.rows(6) * 9;
        Set<Integer> buttonSlots = new HashSet<>();
        for (String id : l.cfg().getSection("buttons").keys()) if (l.hasButton(id)) buttonSlots.addAll(l.slots(id));
        for (ShopManager.Tier t : plugin.shop().tiers()) {
            Map<String, String> taken = new HashMap<>();
            for (ShopManager.Entry e : t.entries()) {
                check(g, e.slot() >= 0 && e.slot() < size, e.id() + " slot " + e.slot() + " is outside the menu");
                check(g, !buttonSlots.contains(e.slot()), e.id() + " is on a button slot (" + e.slot() + ")");
                String prev = taken.put(e.page() + ":" + e.slot(), e.id());
                check(g, prev == null, prev + " and " + e.id() + " share page " + e.page() + " slot " + e.slot());
                for (ShopManager.State st : ShopManager.State.values()) buildItem(g, e.id() + " (" + st.name().toLowerCase() + ")", plugin.shop().displayConfig(e, st).raw(), Material.PAPER);
                Object rawActions = plugin.configs().shop().getSection("tiers." + t.id() + ".entries." + e.id().substring(t.id().length() + 1)).get("actions");
                int raw = rawActions instanceof List<?> list ? list.size() : rawActions == null ? 0 : 1;
                check(g, e.actions().actions().size() == raw, e.id() + " has " + raw + " action(s) in shop.yml but " + e.actions().actions().size() + " parsed");
                durations(g, e.actions(), "shop entry " + e.id());
            }
        }
    }

    private void titlesAndSidebar() {
        for (TitleManager.Title t : plugin.titles().all().values()) {
            try {
                plugin.text().mini().deserialize(t.display());
                pass("titles");
            } catch (RuntimeException e) {
                fail("titles", t.id() + " display: " + e.getMessage());
            }
            Cfg base = plugin.configs().titles().getSection("gui.title-item");
            for (String st : List.of("owned", "equipped", "forced", "locked")) buildItem("titles", t.id() + " (" + st + ")", base.merge(t.icon()).merge(base.getSection(st)).raw(), Material.NAME_TAG);
        }
        List<String> lines = plugin.configs().main().getStringList("sidebar.lines");
        check("sidebar", lines.size() <= 15, "the sidebar has " + lines.size() + " lines; at most 15 show");
        for (String line : lines) {
            try {
                plugin.text().render(line, null, Vars.empty());
                pass("sidebar");
            } catch (RuntimeException e) {
                fail("sidebar", line + ": " + e.getMessage());
            }
        }
    }

    private void lists() {
        Cfg named = plugin.configs().actions().getSection("lists");
        for (String n : named.keys()) {
            var parsed = plugin.actions().named(n);
            int raw = named.isList(n) ? named.getList(n).size() : 1;
            check("action lists", parsed != null && parsed.actions().size() == raw, "list " + n + " has " + raw + " entries but " + (parsed == null ? 0 : parsed.actions().size()) + " parsed (the log says why)");
            if (parsed != null) durations("action lists", parsed, "list " + n);
        }
        Cfg rules = plugin.configs().outcomes().getSection("rules");
        for (String id : rules.keys()) {
            Cfg r = rules.getSection(id);
            if (!r.getBool("enabled", true)) continue;
            var rule = plugin.outcomes().rules().get(id);
            check("outcomes", rule != null, "rule " + id + " did not load (the log says why)");
            if (rule != null) {
                int raw = r.isList("actions") ? r.getList("actions").size() : 1;
                check("outcomes", rule.actions().actions().size() == raw, "rule " + id + " has " + raw + " action(s) but " + rule.actions().actions().size() + " parsed");
                durations("outcomes", rule.actions(), "rule " + id);
            }
        }
        Cfg hooks = plugin.configs().main().getSection("hooks");
        List<String[]> lists = new ArrayList<>();
        for (String h : List.of("cycle-start", "cycle-end")) {
            lists.add(new String[]{"hooks." + h + ".global", h + " global"});
            lists.add(new String[]{"hooks." + h + ".per-player.actions", h + " per-player"});
        }
        for (String w : hooks.getSection("warnings").keys()) lists.add(new String[]{"hooks.warnings." + w + ".global", "warning " + w});
        for (String p : hooks.getSection("player").keys()) lists.add(new String[]{"hooks.player." + p, "player hook " + p});
        for (String[] l : lists) {
            Object raw = plugin.configs().main().get(l[0]);
            if (raw == null) continue;
            int count = raw instanceof List<?> list ? list.size() : 1;
            var list = plugin.actions().parseList(raw, "selftest:" + l[0]);
            int parsed = list.actions().size();
            check("hooks", parsed == count, l[1] + " has " + count + " action(s) but " + parsed + " parsed");
            durations("hooks", list, l[1]);
        }
    }

    /**
     * Actions that exist only while Headcount tracks them (head-protection, extra-heads, bounty, shield, deny,
     * force-title, a locked equip) do nothing with the default duration, instant. Mutes and jails are left out: an
     * instant one means something to the moderation plugin.
     */
    private void durations(String group, com.gradychastain.headcount.action.ActionList list, String where) {
        for (var spec : list.actions()) {
            if (spec.type().equals("run") && !spec.raw().has("actions")) {
                String name = spec.raw().getString("list", "");
                check(group, plugin.actions().named(name) != null, where + ": no action list named '" + name + "' in actions.yml");
            }
            if (spec.type().equals("mute") || spec.type().equals("jail")) continue;
            check(group, !(spec.handler().stateful() && spec.common().duration().instant()),
                    where + ": " + spec.type() + " only works while active, but it has no duration, so it does nothing. Give it one, like cycle, 1d, cycles:2 or permanent.");
        }
    }
}
