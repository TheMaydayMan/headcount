package com.gradychastain.headcount.shop;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionList;
import com.gradychastain.headcount.action.Conditions;
import com.gradychastain.headcount.api.event.ShopPurchaseEvent;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.gui.ConfirmGui;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.item.ItemSpec;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shop from shop.yml. Each tier unlocks at a threshold of the player's score from last cycle and is its own
 * set of pages. Entries cost points and run an action list on purchase.
 */
public final class ShopManager {
    public enum State { AFFORDABLE, TOO_EXPENSIVE, SOLD_OUT, LIMIT_REACHED, COOLDOWN, UNAVAILABLE, LOCKED }

    public record Entry(String id, String tier, String name, int page, int slot, long price, ActionList actions, Conditions conditions,
                        int limitPerCycle, int stock, long cooldownMs, List<String> requireTitles, List<String> requirePurchases,
                        List<String> requirePurchasesThisCycle, List<String> requirePermissions, Cfg display, Cfg states, Boolean confirm) {}

    public record Tier(String id, String name, Integer threshold, List<Entry> entries, int pages, int order) {}

    private final HeadcountPlugin plugin;
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, Integer> sold = new HashMap<>();
    private long confirmAt;
    private int newPlayerScore;
    private boolean stockPerCycle;

    public ShopManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(Cfg file) {
        tiers.clear();
        entries.clear();
        Cfg settings = file.getSection("settings");
        confirmAt = settings.getLong("confirm-at-price", 50);
        newPlayerScore = settings.getInt("new-player-score", 0);
        stockPerCycle = !settings.getString("stock-reset", "cycle").equalsIgnoreCase("never");
        Cfg tierCfg = file.getSection("tiers");
        int order = 0;
        for (String tid : tierCfg.keys()) {
            Cfg t = tierCfg.getSection(tid);
            if (!t.getBool("enabled", true)) continue;
            Object th = t.get("threshold");
            Integer threshold = th == null || String.valueOf(th).equalsIgnoreCase("none") ? null : t.getInt("threshold", 0);
            List<Entry> list = new ArrayList<>();
            Cfg ents = t.getSection("entries");
            int pages = 1;
            for (String eid : ents.keys()) {
                Cfg e = ents.getSection(eid);
                if (!e.getBool("enabled", true)) continue;
                String fullId = tid + "." + eid;
                try {
                    Conditions cond = e.isSection("conditions") ? new Conditions(e.getSection("conditions")) : Conditions.NONE;
                    ActionList actions = plugin.actions().parseList(e.get("actions"), "shop.yml:tiers." + tid + ".entries." + eid + ".actions");
                    Cfg req = e.getSection("requires");
                    int page = Math.max(1, e.getInt("page", 1));
                    pages = Math.max(pages, page);
                    // The name in messages defaults to the display item's name, without formatting.
                    String displayName = e.getString("name", null);
                    if (displayName == null) {
                        String dn = e.getSection("display").getString("name", null);
                        displayName = dn == null ? eid : plugin.text().mini().stripTags(dn);
                    }
                    Entry entry = new Entry(fullId, tid, displayName, page, e.getInt("slot", 0), Math.max(0, e.getLong("price", 0)),
                            actions, cond, e.getInt("limit-per-cycle", 0), e.getInt("stock", -1), e.getMillis("cooldown", 0),
                            req.getStringList("titles"), req.getStringList("purchases"), req.getStringList("purchases-this-cycle"),
                            req.getStringList("permissions"), e.getSection("display"), e.getSection("display-states"),
                            e.has("confirm") ? e.getBool("confirm", false) : null);
                    list.add(entry);
                    entries.put(fullId, entry);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("shop.yml: entry " + fullId + " skipped: " + ex.getMessage());
                }
            }
            tiers.put(tid, new Tier(tid, t.getString("name", tid), threshold, list, Math.max(pages, t.getInt("pages", 1)), order++));
        }
        loadStock();
    }

    private void loadStock() {
        sold.clear();
        int cycle = stockPerCycle ? plugin.cycles().currentId() : 0;
        sold.putAll(plugin.db().sync(c -> {
            Map<String, Integer> out = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT entry, sold FROM shop_stock WHERE cycle=?")) {
                ps.setInt(1, cycle);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                }
            }
            return out;
        }));
    }

    public void resetCycle() {
        if (stockPerCycle) sold.clear();
        for (PlayerData d : plugin.players().onlineData()) d.purchasesThisCycle.clear();
    }

    public List<Tier> tiers() {
        return new ArrayList<>(tiers.values());
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    public MenuLayout layout() {
        return new MenuLayout(plugin.configs().shop().getSection("gui"));
    }

    // ---------------------------------------------------------------- tiers

    public int lastScore(PlayerData d) {
        return d.lastScore == null ? newPlayerScore : d.lastScore;
    }

    public boolean unlocked(Tier t, int lastScore) {
        return t.threshold() == null || lastScore >= t.threshold();
    }

    /** The highest tier a score unlocks, for reports. */
    public String tierNameFor(int score) {
        Tier best = null;
        for (Tier t : tiers.values()) {
            if (!unlocked(t, score)) continue;
            if (best == null || (t.threshold() == null ? Integer.MIN_VALUE : t.threshold()) >= (best.threshold() == null ? Integer.MIN_VALUE : best.threshold())) best = t;
        }
        return best == null ? "" : best.name();
    }

    public Tier highestUnlocked(Player p) {
        PlayerData d = plugin.players().get(p);
        int s = lastScore(d);
        Tier best = null;
        for (Tier t : tiers.values()) if (unlocked(t, s)) best = best == null || (t.threshold() == null ? Integer.MIN_VALUE : t.threshold()) >= (best.threshold() == null ? Integer.MIN_VALUE : best.threshold()) ? t : best;
        return best;
    }

    // ---------------------------------------------------------------- entry state

    public int stockLeft(Entry e) {
        if (e.stock() < 0) return -1;
        return Math.max(0, e.stock() - sold.getOrDefault(e.id(), 0));
    }

    public State state(Player p, Entry e) {
        PlayerData d = plugin.players().get(p);
        Tier t = tiers.get(e.tier());
        if (t == null || !unlocked(t, lastScore(d))) return State.LOCKED;
        if (e.stock() >= 0 && stockLeft(e) <= 0) return State.SOLD_OUT;
        if (e.limitPerCycle() > 0 && d.purchasesThisCycle.getOrDefault(e.id(), 0) >= e.limitPerCycle()) return State.LIMIT_REACHED;
        if (e.cooldownMs() > 0) {
            Long last = d.lastPurchase.get(e.id());
            if (last != null && System.currentTimeMillis() - last < e.cooldownMs()) return State.COOLDOWN;
        }
        for (String perm : e.requirePermissions()) if (!p.hasPermission(perm)) return State.UNAVAILABLE;
        for (String title : e.requireTitles()) if (!d.titles.contains(title.toLowerCase(Locale.ROOT))) return State.UNAVAILABLE;
        for (String req : e.requirePurchases()) if (!d.everPurchased.contains(resolveEntryId(e, req))) return State.UNAVAILABLE;
        for (String req : e.requirePurchasesThisCycle()) if (!d.purchasesThisCycle.containsKey(resolveEntryId(e, req))) return State.UNAVAILABLE;
        if (!e.conditions().test(plugin, p.getUniqueId(), Vars.empty())) return State.UNAVAILABLE;
        if (d.points < e.price()) return State.TOO_EXPENSIVE;
        return State.AFFORDABLE;
    }

    /** Requirements may name an entry by its own ID (same tier) or as tier.entry. */
    private static String resolveEntryId(Entry from, String ref) {
        return ref.contains(".") ? ref : from.tier() + "." + ref;
    }

    public Vars vars(Player p, Entry e) {
        PlayerData d = plugin.players().get(p);
        int bought = d.purchasesThisCycle.getOrDefault(e.id(), 0);
        int stock = stockLeft(e);
        Long last = d.lastPurchase.get(e.id());
        long cd = e.cooldownMs() > 0 && last != null ? Math.max(0, e.cooldownMs() - (System.currentTimeMillis() - last)) : 0;
        Tier t = tiers.get(e.tier());
        String unlimited = plugin.lang().raw("shop.unlimited");
        Vars v = Vars.of("price", e.price(), "points", d.points, "name", e.name(), "bought", bought,
                "cooldown", Formats.duration(cd), "missing", Math.max(0, e.price() - d.points),
                "threshold", t == null || t.threshold() == null ? "-" : t.threshold()).mini("tier", t == null ? "" : t.name());
        // The lang file's "unlimited" text may be formatted, so it goes in as MiniMessage.
        if (stock < 0) v.mini("stock", unlimited);
        else v.put("stock", stock);
        if (e.limitPerCycle() <= 0) v.mini("purchases_left", unlimited).mini("limit", unlimited);
        else v.put("purchases_left", Math.max(0, e.limitPerCycle() - bought)).put("limit", e.limitPerCycle());
        return v;
    }

    /** The config key for a state's look; optional states fall back when neither the entry nor default-states define them. */
    private String stateKey(Entry e, State state) {
        Cfg defaults = plugin.configs().shop().getSection("default-states");
        java.util.function.Predicate<String> defined = k -> e.states().isSection(k) || defaults.isSection(k);
        return switch (state) {
            case AFFORDABLE -> "affordable";
            case TOO_EXPENSIVE -> "too-expensive";
            case SOLD_OUT -> "sold-out";
            case LIMIT_REACHED -> defined.test("limit-reached") ? "limit-reached" : "sold-out";
            case COOLDOWN -> defined.test("cooldown") ? "cooldown" : "too-expensive";
            case UNAVAILABLE -> defined.test("unavailable") ? "unavailable" : "too-expensive";
            case LOCKED -> "locked";
        };
    }

    /**
     * The merged item definition for an entry in a state. Look: the entry's display, then the default state's look,
     * then the entry's own state override. Lore: the entry's own lore followed by the state's lore (the entry's state
     * lore if it has one).
     */
    public Cfg displayConfig(Entry e, State state) {
        String key = stateKey(e, state);
        Cfg base = e.display();
        Cfg defaults = plugin.configs().shop().getSection("default-states." + key);
        Cfg own = e.states().getSection(key);
        Cfg merged = base.merge(defaults).merge(own);
        List<String> lore = new ArrayList<>(base.getStringList("lore"));
        lore.addAll(own.has("lore") ? own.getStringList("lore") : defaults.getStringList("lore"));
        return merged.with("lore", lore);
    }

    public ItemStack display(Player p, Entry e, State state) {
        return ItemSpec.parse(displayConfig(e, state).raw(), Material.PAPER).build(plugin, p, vars(p, e));
    }

    // ---------------------------------------------------------------- buying

    public void click(Player p, Entry e, ShopGui gui) {
        if (plugin.cycles().current() == null) {
            plugin.lang().send(p, "cycle.not-started");
            return;
        }
        if (plugin.cycles().isProcessing()) {
            plugin.lang().send(p, "cycle.processing");
            return;
        }
        State st = state(p, e);
        Vars v = vars(p, e);
        if (st != State.AFFORDABLE) {
            plugin.lang().send(p, "shop.state." + st.name().toLowerCase(Locale.ROOT).replace('_', '-'), v);
            return;
        }
        boolean confirm = e.confirm() != null ? e.confirm() : e.price() >= confirmAt;
        if (confirm) {
            MenuLayout layout = new MenuLayout(plugin.configs().shop().getSection("confirm-gui"));
            new ConfirmGui(plugin, p, layout, v, display(p, e, State.AFFORDABLE), () -> {
                buy(p, e);
                plugin.getServer().getScheduler().runTask(plugin, () -> gui.reopen());
            }, () -> gui.reopen()).open();
            return;
        }
        buy(p, e);
        gui.render();
    }

    private void buy(Player p, Entry e) {
        if (state(p, e) != State.AFFORDABLE) {
            plugin.lang().send(p, "shop.state." + state(p, e).name().toLowerCase(Locale.ROOT).replace('_', '-'), vars(p, e));
            return;
        }
        if (plugin.actions().isDenied(p, "shop", null)) return;
        ShopPurchaseEvent event = new ShopPurchaseEvent(p, e.id(), e.price());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        if (!plugin.points().spend(p.getUniqueId(), e.price(), "shop:" + e.id())) {
            plugin.lang().send(p, "shop.state.too-expensive", vars(p, e));
            return;
        }
        PlayerData d = plugin.players().get(p);
        long now = System.currentTimeMillis();
        int cycle = plugin.cycles().currentId();
        d.purchasesThisCycle.merge(e.id(), 1, Integer::sum);
        d.lastPurchase.put(e.id(), now);
        d.everPurchased.add(e.id());
        if (d.stats != null) d.stats.purchases++;
        plugin.playerStore().addStat(cycle, p.getUniqueId(), "purchases", 1);
        int stockCycle = stockPerCycle ? cycle : 0;
        if (e.stock() >= 0) sold.merge(e.id(), 1, Integer::sum);
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO purchases(cycle, uuid, tier, entry, price, ms) VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1, cycle);
                ps.setString(2, p.getUniqueId().toString());
                ps.setString(3, e.tier());
                ps.setString(4, e.id());
                ps.setLong(5, e.price());
                ps.setLong(6, now);
                ps.executeUpdate();
            }
            if (e.stock() >= 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO shop_stock(entry, cycle, sold) VALUES (?,?,1) ON CONFLICT(entry, cycle) DO UPDATE SET sold = sold + 1")) {
                    ps.setString(1, e.id());
                    ps.setInt(2, stockCycle);
                    ps.executeUpdate();
                }
            }
        });
        plugin.audit().log(AuditType.SHOP_PURCHASE, "player", p.getName() + "(" + p.getUniqueId() + ")", "entry", e.id(), "price", e.price(),
                "points-left", d.points, "cycle", cycle);
        plugin.actions().run(e.actions(), p.getUniqueId(), vars(p, e), "shop:" + e.id());
        plugin.lang().send(p, "shop.purchased", vars(p, e));
    }

    public void open(Player p, String tierId, int page) {
        if (plugin.cycles().current() == null) {
            plugin.lang().send(p, "cycle.not-started");
            return;
        }
        if (tiers.isEmpty()) {
            plugin.lang().send(p, "shop.empty");
            return;
        }
        if (plugin.actions().isDenied(p, "shop", null)) return;
        List<Tier> list = tiers();
        int index = 0;
        if (tierId != null) {
            for (int i = 0; i < list.size(); i++) if (list.get(i).id().equalsIgnoreCase(tierId)) index = i;
        } else {
            Tier best = highestUnlocked(p);
            if (best != null) index = list.indexOf(best);
        }
        new ShopGui(plugin, p, this, index, Math.max(0, page)).open();
    }
}
