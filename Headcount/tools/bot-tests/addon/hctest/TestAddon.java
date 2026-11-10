package hctest;

import com.gradychastain.headcount.api.ConfigView;
import com.gradychastain.headcount.api.HeadcountAPI;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.api.action.ActionType;
import com.gradychastain.headcount.api.event.*;
import com.gradychastain.headcount.api.status.StatusCheck;
import com.gradychastain.headcount.api.status.StatusResolver;
import com.gradychastain.headcount.api.status.StatusSourceType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;

/** Test-only addon: exercises every HeadcountAPI method, logs every event, and changes the changeable ones. */
public final class TestAddon extends JavaPlugin implements Listener {
    private HeadcountAPI api;

    @Override
    public void onEnable() {
        api = Bukkit.getServicesManager().load(HeadcountAPI.class);
        if (api == null) { getLogger().severe("[HCADDON] no HeadcountAPI service"); return; }
        api.registerStatusSource(new StatusSourceType() {
            public String id() { return "in-arena"; }
            public StatusCheck create(ConfigView s, StatusResolver r) {
                String arena = s.getString("arena", "");
                if (arena.isBlank()) throw new IllegalArgumentException("arena is required");
                return p -> p.getScoreboardTags().contains("arena." + arena);
            }
        });
        api.registerActionType(new ActionType() {
            public String id() { return "addon-tag"; }
            public String primaryKey() { return "tag"; }
            public ActionHandler parse(ConfigView s) {
                String tag = s.getString("tag", "addon");
                return new ActionHandler() {
                    public boolean apply(ActionContext ctx) { Player p = ctx.player(); if (p == null) return false; p.addScoreboardTag(tag); return true; }
                    public void revert(ActionContext ctx) { Player p = ctx.player(); if (p != null) p.removeScoreboardTag(tag); }
                    public boolean revertible() { return true; }
                    public String describe(ActionContext ctx) { return "addon tag " + tag; }
                };
            }
        });
        List<Class<? extends Event>> events = List.of(ActionAppliedEvent.class, ActionRevertedEvent.class, CycleEndEvent.class, CycleStartEvent.class,
                KillHeadDropEvent.class, OutcomeApplyEvent.class, PlayerBecameActiveEvent.class, PlayerCycleJoinEvent.class, PlayerFirstJoinEvent.class,
                PointsChangeEvent.class, ScoreCalculatedEvent.class, ShopPurchaseEvent.class, TradeCompleteEvent.class);
        for (Class<? extends Event> c : events) {
            getServer().getPluginManager().registerEvent(c, this, EventPriority.NORMAL, (l, e) -> { if (c.isInstance(e)) handle(e); }, this);
        }
        getLogger().info("[HCADDON] ready: " + events.size() + " events");
    }

    private void handle(Event e) {
        // Previews fire every second for players with the sidebar on: change them, but don't flood the log.
        boolean quiet = e instanceof ScoreCalculatedEvent sc && sc.isPreview();
        StringBuilder sb = new StringBuilder("[HCADDON] EVENT ").append(e.getClass().getSimpleName());
        for (Method m : e.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || m.getDeclaringClass() != e.getClass() || m.getName().equals("getHandlers")) continue;
            if (!m.getName().startsWith("get") && !m.getName().startsWith("is")) continue;
            try {
                Object v = m.invoke(e);
                String s = v instanceof Player p ? p.getName() : String.valueOf(v);
                if (s.length() > 60) s = s.substring(0, 60);
                sb.append(' ').append(m.getName()).append('=').append(s.replace(' ', '_'));
            } catch (ReflectiveOperationException ignored) {
                // skip
            }
        }
        if (!quiet) getLogger().info(sb.toString());
        // the changeable ones, driven by scoreboard tags on the player
        if (e instanceof ScoreCalculatedEvent s && tagged(s.getPlayerId(), "addon-score42")) s.setScore(42);
        if (e instanceof KillHeadDropEvent k && k.getKiller().getScoreboardTags().contains("addon-double")) k.setAmount(k.getAmount() * 2);
        if (e instanceof OutcomeApplyEvent o && tagged(o.getPlayerId(), "addon-nooutcome")) o.setCancelled(true);
        if (e instanceof ShopPurchaseEvent sp && sp.getPlayer().getScoreboardTags().contains("addon-noshop")) sp.setCancelled(true);
        if (e instanceof PointsChangeEvent pc && tagged(pc.getPlayerId(), "addon-points") && pc.getDelta() == 5) pc.setDelta(50);
    }

    private static boolean tagged(UUID id, String tag) {
        Player p = Bukkit.getPlayer(id);
        return p != null && p.getScoreboardTags().contains(tag);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        if (p == null) { sender.sendMessage("[HCADDON] no such player"); return true; }
        UUID id = p.getUniqueId();
        getLogger().info("[HCADDON] API currentCycle=" + api.currentCycle() + " endsInFuture=" + (api.cycleEndsAt() > System.currentTimeMillis()));
        getLogger().info("[HCADDON] API vaultCounts=" + Arrays.toString(api.vaultCounts(id)) + " previewScore=" + api.previewScore(p) + " points=" + api.points(id));
        long after = api.addPoints(id, 3, "addon-test");
        getLogger().info("[HCADDON] API addPoints(3)=" + after + " hasStatus(in-arena-test)=" + api.hasStatus(p, "in-arena-test") + " equippedTitle=" + api.equippedTitle(id));
        ItemStack head = api.createHead(id, "addon");
        var info = api.headInfo(head);
        getLogger().info("[HCADDON] API createHead+headInfo owner=" + info.map(i -> i.ownerName()).orElse("none") + " cycle=" + info.map(i -> i.cycle()).orElse(-1)
                + " current=" + info.map(i -> i.current()).orElse(false) + " source=" + info.map(i -> i.source()).orElse("-") + " plainStone=" + api.headInfo(new ItemStack(org.bukkit.Material.STONE)).isPresent());
        p.getInventory().addItem(head);
        getLogger().info("[HCADDON] API runActionList(ta-message)=" + api.runActionList(id, "ta-message", Map.of("reason", "addon")) + " runActionList(nope)=" + api.runActionList(id, "no-such-list", Map.of()));
        return true;
    }
}
