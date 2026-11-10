package com.example.headcountaddon;

import com.gradychastain.headcount.api.ConfigView;
import com.gradychastain.headcount.api.HeadcountAPI;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.api.action.ActionType;
import com.gradychastain.headcount.api.event.CycleEndEvent;
import com.gradychastain.headcount.api.event.KillHeadDropEvent;
import com.gradychastain.headcount.api.event.ScoreCalculatedEvent;
import com.gradychastain.headcount.api.status.StatusCheck;
import com.gradychastain.headcount.api.status.StatusResolver;
import com.gradychastain.headcount.api.status.StatusSourceType;
import com.gradychastain.headcount.cycle.PlayerResult;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/** The examples from docs/developers.md, compiled with every build so the docs stay correct. */
public final class ExampleAddon extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        HeadcountAPI headcount = Bukkit.getServicesManager().load(HeadcountAPI.class);
        if (headcount == null) return;
        headcount.registerStatusSource(new InArenaSource());
        headcount.registerActionType(new SpeedBoostType());
        getServer().getPluginManager().registerEvents(this, this);
    }

    /** A status source: "source: in-arena" with an optional "arena: name" setting. */
    static final class InArenaSource implements StatusSourceType {
        @Override
        public String id() {
            return "in-arena";
        }

        @Override
        public StatusCheck create(ConfigView settings, StatusResolver statuses) {
            String arena = settings.getString("arena", "");
            if (arena.isBlank()) throw new IllegalArgumentException("arena is required");
            return player -> player.getScoreboardTags().contains("arena:" + arena);
        }
    }

    /** A timed action: "type: speed-boost" with "amount", undone when its duration ends. */
    static final class SpeedBoostType implements ActionType {
        @Override
        public String id() {
            return "speed-boost";
        }

        @Override
        public String primaryKey() {
            return "amount";
        }

        @Override
        public ActionHandler parse(ConfigView settings) {
            double amount = settings.getDouble("amount", 0.02);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    var attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (attr == null) return false;
                    ctx.data().put("before", attr.getBaseValue());
                    attr.setBaseValue(attr.getBaseValue() + amount);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    var attr = p == null ? null : p.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (attr != null && ctx.data().get("before") instanceof Number n) attr.setBaseValue(n.doubleValue());
                }

                @Override
                public boolean revertible() {
                    return true;
                }

                @Override
                public String describe(ActionContext ctx) {
                    return "speed boost +" + amount;
                }
            };
        }
    }

    @EventHandler
    public void onScore(ScoreCalculatedEvent event) {
        // Example: never punish anyone below 2 hours of playtime this cycle.
        Double playtimeHours = event.getVariables().get("playtime_hours");
        if (playtimeHours != null && playtimeHours < 2 && event.getScore() < 0) event.setScore(0);
    }

    @EventHandler
    public void onCycleEnd(CycleEndEvent event) {
        for (PlayerResult r : event.getResults().top(3)) {
            getLogger().info("Cycle " + event.getResults().cycle().id() + " top: " + r.name() + " with " + r.unique() + " unique heads");
        }
    }

    @EventHandler
    public void onKillHead(KillHeadDropEvent event) {
        if (event.getKiller().hasPermission("myserver.doubleheads")) event.setAmount(event.getAmount() * 2);
    }

    void runList(HeadcountAPI headcount, Player player) {
        headcount.runActionList(player.getUniqueId(), "celebrate", Map.of("reason", "event win"));
    }
}
