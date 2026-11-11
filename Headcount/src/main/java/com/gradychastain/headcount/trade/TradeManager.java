package com.gradychastain.headcount.trade;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.event.TradeCompleteEvent;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.status.StatusGate;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trades. Each player can be in at most one request or trade at a time, incoming or outgoing. Offers are saved to
 * the database as they change, so a crash returns them on the owner's next join. Anything that makes a trade
 * unsafe cancels it and returns every item to its owner.
 */
public final class TradeManager implements Listener {
    private final HeadcountPlugin plugin;
    private TradeRules rules;
    private MenuLayout layout;
    private int[] ownSlots;
    private int[] otherSlots;
    private StatusGate sendGate, sendTargetGate, acceptGate, acceptTargetGate, cancelGate;
    private final Map<UUID, TradeRequest> requests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private BukkitTask checker;

    public TradeManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    TradeRules rules() {
        return rules;
    }

    public void load(Cfg cfg) {
        rules = new TradeRules(cfg);
        layout = new MenuLayout(cfg.getSection("gui"));
        int size = layout.rows(6) * 9;
        int[] o = cfg.getSection("gui").getIntList("own-slots").stream().mapToInt(Integer::intValue).toArray();
        int[] t = cfg.getSection("gui").getIntList("other-slots").stream().mapToInt(Integer::intValue).toArray();
        if (o.length == 0 || o.length != t.length || java.util.Arrays.stream(o).anyMatch(s -> s < 0 || s >= size)
                || java.util.Arrays.stream(t).anyMatch(s -> s < 0 || s >= size)) {
            plugin.getLogger().warning("trade.yml gui.own-slots and gui.other-slots must be lists of the same length that fit in the menu. Using the default layout.");
            o = new int[]{0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39, 45, 46, 47, 48};
            t = new int[]{5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44, 50, 51, 52, 53};
        }
        ownSlots = o;
        otherSlots = t;
        sendGate = new StatusGate(plugin, "trade-send", rules.blockSend);
        sendTargetGate = new StatusGate(plugin, "trade-send-target", rules.blockSend);
        acceptGate = new StatusGate(plugin, "trade-accept", rules.blockAccept);
        acceptTargetGate = new StatusGate(plugin, "trade-accept-target", rules.blockAccept);
        cancelGate = new StatusGate(plugin, "trade-cancel", rules.cancelOpen);
        if (checker != null) checker.cancel();
        checker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, rules.checkIntervalTicks, rules.checkIntervalTicks);
    }

    public boolean isBusy(UUID p) {
        return requests.containsKey(p) || sessions.containsKey(p);
    }

    public boolean inTrade(UUID p) {
        return sessions.containsKey(p);
    }

    public long cooldownLeft(UUID p) {
        Long until = cooldowns.get(p);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    // ---------------------------------------------------------------- requests

    private boolean onCooldown(Player p) {
        return cooldownLeft(p.getUniqueId()) > 0 && !p.hasPermission("headcount.bypass.trade.cooldown");
    }

    private boolean inRange(Player a, Player b) {
        if (!a.getWorld().equals(b.getWorld())) return !rules.sameWorld;
        if (rules.range < 0 || a.hasPermission("headcount.bypass.trade.range")) return true;
        return a.getLocation().distanceSquared(b.getLocation()) <= rules.range * rules.range;
    }

    private boolean hidden(Player viewer, Player target) {
        return !viewer.canSee(target) || (rules.vanishedStatus != null && !rules.vanishedStatus.isBlank() && plugin.statuses().has(target, rules.vanishedStatus));
    }

    public void request(Player sender, String targetName) {
        Vars v = Vars.of("player", sender.getName(), "target", targetName);
        if (plugin.cycles().isProcessing()) {
            plugin.lang().send(sender, "cycle.processing");
            return;
        }
        if (plugin.actions().isDenied(sender, "trade", null)) return;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || hidden(sender, target)) {
            plugin.lang().send(sender, "trade.request.offline", v);
            return;
        }
        v.put("target", target.getName());
        if (target.equals(sender)) {
            plugin.lang().send(sender, "trade.request.self", v);
            return;
        }
        if (isBusy(sender.getUniqueId())) {
            plugin.lang().send(sender, "trade.request.you-busy", v);
            return;
        }
        if (isBusy(target.getUniqueId())) {
            plugin.lang().send(sender, "trade.request.target-busy", v);
            return;
        }
        if (onCooldown(sender)) {
            plugin.lang().send(sender, "trade.request.you-cooldown", v.copy().put("time", Formats.duration(cooldownLeft(sender.getUniqueId()))));
            return;
        }
        if (onCooldown(target)) {
            plugin.lang().send(sender, "trade.request.target-cooldown", v.copy().put("time", Formats.duration(cooldownLeft(target.getUniqueId()))));
            return;
        }
        if (!sender.getWorld().equals(target.getWorld()) && rules.sameWorld) {
            plugin.lang().send(sender, "trade.request.other-world", v);
            return;
        }
        if (!inRange(sender, target)) {
            plugin.lang().send(sender, "trade.request.out-of-range", v.copy().put("range", Formats.number(rules.range)));
            return;
        }
        if (sendGate.check(sender, v)) return;
        String targetBlock = sendTargetGate.blocking(target);
        if (targetBlock != null) {
            sendTargetGate.notify(sender, targetBlock, v);
            return;
        }
        if (plugin.actions().handlers(target.getUniqueId(), com.gradychastain.headcount.action.types.DenyAction.Handler.class).stream()
                .anyMatch(e -> e.getKey().denies("trade", null)) && !target.hasPermission("headcount.bypass.deny.trade")) {
            plugin.lang().send(sender, "trade.request.target-cannot", v);
            return;
        }
        long now = System.currentTimeMillis();
        TradeRequest r = new TradeRequest(sender.getUniqueId(), target.getUniqueId(), now, now + rules.requestTimeoutMs);
        requests.put(sender.getUniqueId(), r);
        requests.put(target.getUniqueId(), r);
        r.expiry = plugin.getServer().getScheduler().runTaskLater(plugin, () -> expire(r), Math.max(1, rules.requestTimeoutMs / 50));
        Vars rv = v.copy().put("seconds", rules.requestTimeoutMs / 1000);
        plugin.lang().send(sender, "trade.request.sent", rv);
        boolean bedrock = plugin.integrations().isBedrock(target.getUniqueId());
        plugin.lang().send(target, bedrock ? "trade.request.received-bedrock" : "trade.request.received", rv);
        plugin.audit().log(AuditType.TRADE_REQUEST, "sender", sender.getName() + "(" + sender.getUniqueId() + ")", "target", target.getName() + "(" + target.getUniqueId() + ")");
    }

    private void removeRequest(TradeRequest r) {
        requests.remove(r.sender, r);
        requests.remove(r.target, r);
        if (r.expiry != null) r.expiry.cancel();
    }

    private void expire(TradeRequest r) {
        if (requests.get(r.sender) != r) return;
        removeRequest(r);
        Player s = Bukkit.getPlayer(r.sender), t = Bukkit.getPlayer(r.target);
        Vars v = Vars.of("player", s == null ? "?" : s.getName(), "target", t == null ? "?" : t.getName());
        if (s != null) plugin.lang().send(s, "trade.request.expired-sender", v);
        if (t != null) plugin.lang().send(t, "trade.request.expired-target", v);
    }

    public void accept(Player target) {
        TradeRequest r = requests.get(target.getUniqueId());
        if (r == null || !r.target.equals(target.getUniqueId())) {
            plugin.lang().send(target, "trade.request.none-incoming");
            return;
        }
        Player sender = Bukkit.getPlayer(r.sender);
        Vars v = Vars.of("player", sender == null ? "?" : sender.getName(), "target", target.getName());
        if (sender == null || hidden(target, sender)) {
            removeRequest(r);
            plugin.lang().send(target, "trade.request.offline", v.copy().put("target", sender == null ? "?" : sender.getName()));
            return;
        }
        if (plugin.cycles().isProcessing()) {
            plugin.lang().send(target, "cycle.processing");
            return;
        }
        if (plugin.actions().isDenied(target, "trade", null)) return;
        if (!inRange(sender, target)) {
            plugin.lang().send(target, !sender.getWorld().equals(target.getWorld()) ? "trade.request.other-world" : "trade.request.out-of-range",
                    v.copy().put("target", sender.getName()).put("range", Formats.number(rules.range)));
            return;
        }
        if (onCooldown(sender) || onCooldown(target)) {
            plugin.lang().send(target, "trade.request.target-cooldown", v.copy().put("target", sender.getName()).put("time", Formats.duration(Math.max(cooldownLeft(sender.getUniqueId()), cooldownLeft(target.getUniqueId())))));
            return;
        }
        if (acceptGate.check(target, v)) return;
        String senderBlock = acceptTargetGate.blocking(sender);
        if (senderBlock != null) {
            acceptTargetGate.notify(target, senderBlock, v.copy().put("target", sender.getName()));
            return;
        }
        removeRequest(r);
        open(sender, target);
    }

    public void deny(Player target) {
        TradeRequest r = requests.get(target.getUniqueId());
        if (r == null || !r.target.equals(target.getUniqueId())) {
            plugin.lang().send(target, "trade.request.none-incoming");
            return;
        }
        removeRequest(r);
        Player s = Bukkit.getPlayer(r.sender);
        Vars v = Vars.of("player", s == null ? "?" : s.getName(), "target", target.getName());
        plugin.lang().send(target, "trade.request.denied-target", v);
        if (s != null) plugin.lang().send(s, "trade.request.denied-sender", v);
    }

    /** /trade cancel: withdraws an outgoing request, or cancels the open trade. */
    public void cancelCommand(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s != null) {
            cancel(s, "cancelled-by", p);
            return;
        }
        TradeRequest r = requests.get(p.getUniqueId());
        if (r == null) {
            plugin.lang().send(p, "trade.request.none");
            return;
        }
        removeRequest(r);
        Player other = Bukkit.getPlayer(r.other(p.getUniqueId()));
        Vars v = Vars.of("player", p.getName(), "target", other == null ? "?" : other.getName());
        plugin.lang().send(p, "trade.request.cancelled-self", v);
        if (other != null) plugin.lang().send(other, "trade.request.cancelled-other", v);
    }

    // ---------------------------------------------------------------- sessions

    private void open(Player a, Player b) {
        TradeSession s = new TradeSession(a, b);
        s.viewA = new TradeGui(plugin, this, s, a, layout, ownSlots, otherSlots);
        s.viewB = new TradeGui(plugin, this, s, b, layout, ownSlots, otherSlots);
        sessions.put(a.getUniqueId(), s);
        sessions.put(b.getUniqueId(), s);
        s.viewA.render();
        s.viewB.render();
        a.openInventory(s.viewA.getInventory());
        b.openInventory(s.viewB.getInventory());
        plugin.audit().log(AuditType.TRADE_START, "trade", s.id, "a", a.getName() + "(" + a.getUniqueId() + ")", "b", b.getName() + "(" + b.getUniqueId() + ")");
        Vars v = Vars.of("other", b.getName());
        plugin.lang().send(a, "trade.opened", v);
        plugin.lang().send(b, "trade.opened", Vars.of("other", a.getName()));
    }

    /** heads (current-cycle economy heads, by amount) and item stacks (everything else). */
    int[] counts(List<ItemStack> items) {
        int heads = 0, stacks = 0;
        for (ItemStack i : items) {
            if (Items.isEmpty(i)) continue;
            if (plugin.heads().readCurrent(i) != null) heads += i.getAmount();
            else stacks++;
        }
        return new int[]{heads, stacks};
    }

    boolean itemAllowed(Player p, ItemStack item, boolean tell) {
        if (Items.isEmpty(item)) return true;
        var pdc = item.getPersistentDataContainer();
        if (pdc.has(plugin.heads().keys().guiItem) || pdc.has(plugin.heads().keys().lockedItem)) {
            if (tell) plugin.lang().send(p, "trade.item-not-allowed");
            return false;
        }
        if (rules.blockedItems.contains(item.getType())) {
            if (tell) plugin.lang().send(p, "trade.item-not-allowed");
            return false;
        }
        HeadData h = plugin.heads().readCurrent(item);
        if (h != null) {
            if (rules.onlyOwnHeads && !h.owner().equals(p.getUniqueId())) {
                if (tell) plugin.lang().send(p, "trade.only-own-heads");
                return false;
            }
            if (item.getAmount() > 1) {
                if (tell) plugin.lang().send(p, "vault.duplicate-head");
                plugin.audit().log(AuditType.DUPLICATE, "kind", "stacked-copies-in-trade", "head", h.id(), "player", p.getName() + "(" + p.getUniqueId() + ")");
                return false;
            }
        }
        return true;
    }

    boolean validOffer(Player p, List<ItemStack> offer, boolean tell) {
        int[] c = counts(offer);
        if (c[0] > rules.maxHeads) {
            if (tell) plugin.lang().send(p, "trade.too-many-heads", "max", rules.maxHeads);
            return false;
        }
        if (c[1] > rules.maxItems) {
            if (tell) plugin.lang().send(p, "trade.too-many-items", "max", rules.maxItems);
            return false;
        }
        return true;
    }

    /** Whether placing {@code incoming} into an own slot that holds {@code replaced} keeps the offer valid. */
    boolean canOffer(Player p, TradeGui view, ItemStack incoming, ItemStack replaced, int amountOverride) {
        if (Items.isEmpty(incoming)) return true;
        if (!itemAllowed(p, incoming, true)) return false;
        List<ItemStack> after = new ArrayList<>(view.readOwn());
        if (!Items.isEmpty(replaced)) {
            boolean merges = replaced.isSimilar(incoming);
            if (!merges) {
                for (int i = 0; i < after.size(); i++) {
                    if (after.get(i).equals(replaced)) {
                        after.remove(i);
                        break;
                    }
                }
            }
            if (merges) return validOffer(p, after, true);
        }
        ItemStack add = incoming.clone();
        if (amountOverride > 0) add.setAmount(amountOverride);
        after.add(add);
        return validOffer(p, after, true);
    }

    /** Called after any change to a player's offer: clears both confirmations, mirrors, saves. */
    void offerChanged(TradeSession s, Player changer) {
        if (s.finished()) return;
        // Re-validate the whole offer; anything over the limits goes back to its owner.
        TradeGui v = s.view(changer);
        List<ItemStack> offer = v.readOwn();
        for (ItemStack i : offer) {
            if (!itemAllowed(changer, i, false)) {
                for (int slot : v.ownSlots()) {
                    ItemStack cur = v.getInventory().getItem(slot);
                    if (cur != null && cur.equals(i)) {
                        v.getInventory().setItem(slot, null);
                        Items.giveOrDrop(changer, cur);
                        break;
                    }
                }
            }
        }
        boolean wasConfirmed = s.confirmA || s.confirmB;
        s.confirmA = false;
        s.confirmB = false;
        if (s.state == TradeSession.State.COUNTDOWN) stopCountdown(s);
        if (wasConfirmed) {
            plugin.lang().send(s.a, "trade.changed");
            plugin.lang().send(s.b, "trade.changed");
        }
        save(s, changer);
        s.viewA.render();
        s.viewB.render();
    }

    private void save(TradeSession s, Player owner) {
        List<byte[]> items = new ArrayList<>();
        for (ItemStack i : s.offer(owner)) items.add(Items.toBytes(i));
        String trade = s.id.toString(), uuid = owner.getUniqueId().toString();
        plugin.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM trade_items WHERE trade_id=? AND owner=?")) {
                ps.setString(1, trade);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO trade_items(trade_id, owner, slot, item) VALUES (?,?,?,?)")) {
                for (int i = 0; i < items.size(); i++) {
                    ps.setString(1, trade);
                    ps.setString(2, uuid);
                    ps.setInt(3, i);
                    ps.setBytes(4, items.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
        // The item left (or came back to) the player's inventory: save it too, or a crash could dupe or lose it.
        if (rules.savePlayerData) plugin.players().scheduleSave(owner);
    }

    private void deleteSaved(TradeSession s) {
        String trade = s.id.toString();
        plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM trade_items WHERE trade_id=?")) {
                ps.setString(1, trade);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** The trade kinds present, checked against allowed-types. Returns a lang key when not allowed, else null. */
    private String typeProblem(TradeSession s) {
        int[] a = counts(s.offer(s.a)), b = counts(s.offer(s.b));
        boolean aHeads = a[0] > 0, aItems = a[1] > 0, bHeads = b[0] > 0, bItems = b[1] > 0;
        boolean aEmpty = !aHeads && !aItems, bEmpty = !bHeads && !bItems;
        if (aEmpty && bEmpty) return "trade.empty";
        if (aEmpty || bEmpty) return rules.gifts ? null : "trade.type-gift";
        if (aHeads && bHeads && !rules.headsForHeads) return "trade.type-heads-for-heads";
        if (((aHeads && bItems) || (aItems && bHeads)) && !rules.itemsForHeads) return "trade.type-items-for-heads";
        if (aItems && bItems && !rules.itemsForItems) return "trade.type-items-for-items";
        return null;
    }

    void toggleConfirm(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null || s.finished()) return;
        if (s.state == TradeSession.State.COUNTDOWN) {
            s.setConfirmed(p, false);
            stopCountdown(s);
            plugin.lang().send(s.other(p), "trade.unconfirmed-other", "other", p.getName());
            s.viewA.render();
            s.viewB.render();
            return;
        }
        boolean now = !s.confirmed(p);
        if (now) {
            String problem = typeProblem(s);
            if (problem != null) {
                plugin.lang().send(p, problem);
                return;
            }
            if (!validOffer(s.a, s.offer(s.a), false) || !validOffer(s.b, s.offer(s.b), false)) {
                plugin.lang().send(p, "trade.invalid");
                return;
            }
        }
        s.setConfirmed(p, now);
        plugin.lang().send(s.other(p), now ? "trade.confirmed-other" : "trade.unconfirmed-other", "other", p.getName());
        if (s.confirmA && s.confirmB) startCountdown(s);
        s.viewA.render();
        s.viewB.render();
    }

    private void startCountdown(TradeSession s) {
        s.state = TradeSession.State.COUNTDOWN;
        s.confirmedSnapshot = s.snapshot();
        s.secondsLeft = (int) Math.ceil(rules.countdownMs / 1000.0);
        if (s.secondsLeft <= 0) {
            execute(s);
            return;
        }
        s.countdown = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (s.state != TradeSession.State.COUNTDOWN) return;
            if (s.secondsLeft <= 0) {
                s.countdown.cancel();
                execute(s);
                return;
            }
            plugin.lang().actionBar(s.a, "trade.countdown", Vars.of("seconds", s.secondsLeft));
            plugin.lang().actionBar(s.b, "trade.countdown", Vars.of("seconds", s.secondsLeft));
            s.viewA.render();
            s.viewB.render();
            s.secondsLeft--;
        }, 0L, 20L);
    }

    private void stopCountdown(TradeSession s) {
        if (s.countdown != null) s.countdown.cancel();
        s.countdown = null;
        s.state = TradeSession.State.OPEN;
        s.confirmA = false;
        s.confirmB = false;
    }

    private boolean viewing(Player p, TradeGui view) {
        return p.isOnline() && p.getOpenInventory().getTopInventory().equals(view.getInventory());
    }

    private void execute(TradeSession s) {
        if (s.state != TradeSession.State.COUNTDOWN) return;
        if (!viewing(s.a, s.viewA) || !viewing(s.b, s.viewB)) {
            cancel(s, "closed", null);
            return;
        }
        String problem = unsafe(s);
        if (problem != null) {
            cancel(s, problem, null);
            return;
        }
        if (!s.snapshot().equals(s.confirmedSnapshot)) {
            cancel(s, "changed", null);
            return;
        }
        s.state = TradeSession.State.EXECUTING;
        List<ItemStack> fromA = s.takeAll(s.a);
        List<ItemStack> fromB = s.takeAll(s.b);
        s.viewA.clearMirror();
        s.viewB.clearMirror();
        s.state = TradeSession.State.DONE;
        sessions.remove(s.a.getUniqueId(), s);
        sessions.remove(s.b.getUniqueId(), s);
        s.viewA.closingInternally = true;
        s.viewB.closingInternally = true;
        s.a.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLUGIN);
        s.b.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLUGIN);
        int droppedA = Items.giveOrDrop(s.a, fromB);
        int droppedB = Items.giveOrDrop(s.b, fromA);
        if (droppedA > 0) plugin.lang().send(s.a, "items.inventory-full", "count", droppedA);
        if (droppedB > 0) plugin.lang().send(s.b, "items.inventory-full", "count", droppedB);
        deleteSaved(s);
        if (rules.savePlayerData) {
            s.a.saveData();
            s.b.saveData();
        }
        int[] ca = counts(fromA), cb = counts(fromB);
        stat(s.a, ca[0], cb[0]);
        stat(s.b, cb[0], ca[0]);
        long until = System.currentTimeMillis() + rules.cooldownMs;
        cooldowns.put(s.a.getUniqueId(), until);
        cooldowns.put(s.b.getUniqueId(), until);
        plugin.audit().log(AuditType.TRADE_COMPLETE, "trade", s.id, "a", s.a.getName() + "(" + s.a.getUniqueId() + ")",
                "b", s.b.getName() + "(" + s.b.getUniqueId() + ")", "a-gave", describe(fromA), "b-gave", describe(fromB));
        Bukkit.getPluginManager().callEvent(new TradeCompleteEvent(s.a, s.b, List.copyOf(fromA), List.copyOf(fromB)));
        plugin.lang().send(s.a, "trade.complete", "other", s.b.getName());
        plugin.lang().send(s.b, "trade.complete", "other", s.a.getName());
    }

    private void stat(Player p, int headsOut, int headsIn) {
        int cycle = plugin.cycles().currentId();
        if (cycle <= 0) return;
        PlayerData d = plugin.players().get(p.getUniqueId());
        if (d != null && d.stats != null) {
            d.stats.trades++;
            d.stats.headsIn += headsIn;
            d.stats.headsOut += headsOut;
        }
        plugin.playerStore().addStat(cycle, p.getUniqueId(), "trades", 1);
        if (headsIn > 0) plugin.playerStore().addStat(cycle, p.getUniqueId(), "heads_in", headsIn);
        if (headsOut > 0) plugin.playerStore().addStat(cycle, p.getUniqueId(), "heads_out", headsOut);
    }

    private String describe(List<ItemStack> items) {
        List<String> parts = new ArrayList<>();
        for (ItemStack i : items) {
            HeadData h = plugin.heads().read(i);
            if (h != null) parts.add("HEAD[" + h.id() + ",owner=" + h.ownerName() + ",cycle=" + h.cycle() + "]");
            else {
                String custom = plugin.integrations().customIdOf(i);
                parts.add(Items.describe(i) + (custom != null ? "[" + custom + "]" : "") + (i.hasItemMeta() && i.getItemMeta().hasDisplayName() ? "[named]" : ""));
            }
        }
        return parts.isEmpty() ? "nothing" : String.join(";", parts);
    }

    /** A reason key if the trade can no longer safely continue, else null. */
    private String unsafe(TradeSession s) {
        if (!s.a.isOnline() || !s.b.isOnline()) return "disconnected";
        if (rules.sameWorld && !s.a.getWorld().equals(s.b.getWorld())) return "world";
        if (!inRange(s.a, s.b)) return "range";
        for (Player p : List.of(s.a, s.b)) {
            String st = cancelGate.blocking(p);
            if (st != null) return "status:" + st + ":" + p.getName();
            if (plugin.actions().handlers(p.getUniqueId(), com.gradychastain.headcount.action.types.DenyAction.Handler.class).stream()
                    .anyMatch(e -> e.getKey().denies("trade", null)) && !p.hasPermission("headcount.bypass.deny.trade")) return "denied";
        }
        if (plugin.cycles().isProcessing()) return "cycle-end";
        return null;
    }

    /** Cancels a trade and returns everything to its owner. {@code by} is the player who caused it, if any. */
    void cancel(TradeSession s, String reason, Player by) {
        if (s.finished()) return;
        if (s.countdown != null) s.countdown.cancel();
        s.state = TradeSession.State.CANCELLED;
        sessions.remove(s.a.getUniqueId(), s);
        sessions.remove(s.b.getUniqueId(), s);
        List<ItemStack> backA = s.takeAll(s.a);
        List<ItemStack> backB = s.takeAll(s.b);
        s.viewA.clearMirror();
        s.viewB.clearMirror();
        returnItems(s.a, backA);
        returnItems(s.b, backB);
        deleteSaved(s);
        s.viewA.closingInternally = true;
        s.viewB.closingInternally = true;
        for (Player p : List.of(s.a, s.b)) {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().equals(s.view(p).getInventory())) {
                p.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLUGIN);
            }
        }
        if (rules.cooldownAfterCancel) {
            long until = System.currentTimeMillis() + rules.cooldownMs;
            cooldowns.put(s.a.getUniqueId(), until);
            cooldowns.put(s.b.getUniqueId(), until);
        }
        plugin.audit().log(AuditType.TRADE_CANCEL, "trade", s.id, "reason", reason, "by", by == null ? null : by.getName(),
                "returned-a", describe(backA), "returned-b", describe(backB));
        String key = reason.startsWith("status:") ? "trade.cancelled.status" : "trade.cancelled." + reason;
        if (!plugin.lang().has(key)) key = "trade.cancelled.generic";
        String statusName = reason.startsWith("status:") ? reason.split(":")[1] : "";
        String who = reason.startsWith("status:") ? reason.split(":")[2] : by == null ? "" : by.getName();
        for (Player p : List.of(s.a, s.b)) {
            if (!p.isOnline()) continue;
            plugin.lang().send(p, key, Vars.of("other", s.other(p).getName(), "player", who, "status", statusName, "reason", reason));
        }
    }

    private void returnItems(Player owner, List<ItemStack> items) {
        if (items.isEmpty()) return;
        for (ItemStack i : items) plugin.heads().refresh(i);
        if (owner.isOnline() && !owner.isDead()) {
            int dropped = Items.giveOrDrop(owner, items);
            if (dropped > 0) plugin.lang().send(owner, "items.inventory-full", "count", dropped);
            if (rules.savePlayerData) plugin.players().scheduleSave(owner);
        } else {
            // Offline, or dead (a dead player's inventory is not carried over on respawn): deliver later.
            plugin.deliveries().queue(owner.getUniqueId(), items, "trade-return");
        }
    }

    /** Cancels every open trade and request (plugin disable, reload, cycle end). */
    public void cancelAll(String reason) {
        for (TradeSession s : new ArrayList<>(new java.util.LinkedHashSet<>(sessions.values()))) cancel(s, reason, null);
        for (TradeRequest r : new ArrayList<>(new java.util.LinkedHashSet<>(requests.values()))) removeRequest(r);
    }

    /** Admin cancel. */
    public boolean adminCancel(UUID player, String actor) {
        TradeSession s = sessions.get(player);
        if (s != null) {
            plugin.audit().log(AuditType.ADMIN, "action", "trade-cancel", "trade", s.id, "actor", actor);
            cancel(s, "admin", null);
            return true;
        }
        TradeRequest r = requests.get(player);
        if (r != null) {
            removeRequest(r);
            return true;
        }
        return false;
    }

    /** Periodic safety check for every open trade. */
    private void check() {
        for (TradeSession s : new ArrayList<>(new java.util.LinkedHashSet<>(sessions.values()))) {
            if (s.finished()) continue;
            if (!viewing(s.a, s.viewA) || !viewing(s.b, s.viewB)) {
                cancel(s, "closed", null);
                continue;
            }
            String problem = unsafe(s);
            if (problem != null) cancel(s, problem, null);
        }
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(t -> t < now);
    }

    // ---------------------------------------------------------------- crash recovery

    /** Returns items left in trades that were open when the server stopped unexpectedly. */
    public void recover(Player p) {
        List<ItemStack> items = plugin.db().sync(c -> {
            List<ItemStack> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT item FROM trade_items WHERE owner=?")) {
                ps.setString(1, p.getUniqueId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            ItemStack i = Items.fromBytes(rs.getBytes(1));
                            if (i != null) out.add(i);
                        } catch (RuntimeException e) {
                            plugin.getLogger().warning("A saved trade item for " + p.getName() + " could not be read: " + e.getMessage());
                        }
                    }
                }
            }
            if (!out.isEmpty() && !sessions.containsKey(p.getUniqueId())) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM trade_items WHERE owner=?")) {
                    ps.setString(1, p.getUniqueId().toString());
                    ps.executeUpdate();
                }
            }
            return out;
        });
        if (sessions.containsKey(p.getUniqueId())) return;
        items.removeIf(i -> plugin.refresher().removeIfVoid(i, "trade-recovery:" + p.getName() + "(" + p.getUniqueId() + ")"));
        if (items.isEmpty()) return;
        returnItems(p, items);
        plugin.lang().send(p, "trade.recovered", "count", items.size());
        plugin.audit().log(AuditType.TRADE_RETURN, "player", p.getName() + "(" + p.getUniqueId() + ")", "items", describe(items), "reason", "crash-recovery");
    }

    // ---------------------------------------------------------------- cancel triggers

    @EventHandler(priority = EventPriority.LOW)
    public void onKick(PlayerKickEvent event) {
        TradeSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null) cancel(s, "kicked", event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        TradeSession s = sessions.get(id);
        if (s != null) cancel(s, "disconnected", event.getPlayer());
        TradeRequest r = requests.get(id);
        if (r != null) {
            removeRequest(r);
            Player other = Bukkit.getPlayer(r.other(id));
            if (other != null) plugin.lang().send(other, "trade.request.cancelled-other", Vars.of("player", event.getPlayer().getName()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null || s.finished()) return;
        // The dying player's offer joins their death drops (or stays with them under keepInventory).
        boolean keep = event.getKeepInventory();
        List<ItemStack> mine = s.takeAll(p);
        if (!keep) event.getDrops().addAll(mine);
        else {
            int dropped = Items.giveOrDrop(p, mine);
            if (dropped > 0) plugin.lang().send(p, "items.inventory-full", "count", dropped);
        }
        cancel(s, "death", p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorld(PlayerChangedWorldEvent event) {
        TradeSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null) cancel(s, "world", event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        TradeSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null) return;
        // A world change closes the trade screen before PlayerChangedWorldEvent fires, so give the real reason here.
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            cancel(s, "world", event.getPlayer());
            return;
        }
        if (!rules.cancelOnTeleport || event.getFrom().distance(event.getTo()) <= rules.teleportThreshold) return;
        cancel(s, "teleport", event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGamemode(PlayerGameModeChangeEvent event) {
        TradeSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null && rules.cancelOnGamemode) cancel(s, "gamemode", event.getPlayer());
    }

    public void shutdown() {
        if (checker != null) checker.cancel();
        cancelAll("shutdown");
    }
}
