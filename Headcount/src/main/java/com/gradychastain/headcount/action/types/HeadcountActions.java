package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionList;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.util.Cfg;

/**
 * next-cycle, rollover, head-protection, extra-heads, bounty, shield, flag, score-bonus.
 * Protection, extra heads, bounties and shields are effects that exist while tracked; their {@code charges}
 * (0 = unlimited) are used up as they trigger.
 */
public final class HeadcountActions {
    private HeadcountActions() {}

    /** An effect with optional charges. */
    public abstract static class Charged implements ActionHandler {
        final int charges;

        Charged(Cfg c, int def) {
            this.charges = Math.max(0, c.getInt("charges", def));
        }

        /** Total charges (0 = unlimited). */
        public int getCharges() {
            return charges;
        }

        /** Uses one charge; returns true if the effect should end now. */
        public boolean use(HeadcountPlugin plugin, ActionContext ctx) {
            if (charges <= 0) return false;
            int used = ctx.data().get("used") instanceof Number n ? n.intValue() + 1 : 1;
            ctx.data().put("used", used);
            if (ctx.instanceId() != null) {
                if (used >= charges) {
                    plugin.actions().end(ctx.instanceId(), "used-up");
                    return true;
                }
                plugin.actions().save(ctx.instanceId());
            }
            return false;
        }

        @Override public boolean apply(ActionContext ctx) { return true; }
        @Override public boolean stateful() { return true; }
        @Override public boolean requiresOnline() { return false; }
    }

    public static final class HeadProtection extends Charged {
        HeadProtection(Cfg c) { super(c, 0); }
        @Override public String describe(ActionContext ctx) { return "your head will not drop when you are killed"; }
    }

    public static final class ExtraHeads extends Charged {
        public final int amount;

        ExtraHeads(Cfg c) {
            super(c, 0);
            amount = Math.max(1, c.getInt("amount", 1));
        }

        @Override public String describe(ActionContext ctx) { return amount + " extra head(s) drop when you are killed"; }
    }

    public static final class Bounty extends Charged {
        public final long points;
        public final ActionList rewards;
        public final boolean announce;

        Bounty(Cfg c, HeadcountPlugin plugin, String path) {
            super(c, c.getInt("claims", 1));
            points = c.getLong("points", 0);
            rewards = c.has("actions") ? plugin.actions().parseList(c.get("actions"), path + ".actions") : ActionList.EMPTY;
            announce = c.getBool("announce", true);
        }

        @Override
        public boolean apply(ActionContext ctx) {
            if (announce) {
                HeadcountPlugin plugin = Types.impl(ctx).plugin();
                var v = Types.impl(ctx).vars().copy().put("player", Types.impl(ctx).playerName()).put("points", points);
                for (var p : plugin.getServer().getOnlinePlayers()) plugin.lang().send(p, "bounty.placed", v);
            }
            return true;
        }

        @Override public String describe(ActionContext ctx) { return "bounty on your head" + (points > 0 ? " (" + points + " points)" : ""); }
    }

    public static final class Shield extends Charged {
        Shield(Cfg c) { super(c, 1); }
        @Override public String describe(ActionContext ctx) { return "shield against the next punishment"; }
    }

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("next-cycle", "starting-heads", c -> {
            String heads = c.getString("starting-heads", null);
            String size = c.getString("vault-size", null);
            if (heads == null && size == null) throw new IllegalArgumentException("next-cycle needs starting-heads and/or vault-size (e.g. \"+5\", \"-3\" or \"=10\")");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    if (heads != null) plugin.vaults().addNextCycleMod(ctx.playerId(), false, normalize(heads));
                    if (size != null) plugin.vaults().addNextCycleMod(ctx.playerId(), true, normalize(size));
                    return true;
                }

                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("rollover", "percent", c -> {
            boolean set = c.has("percent");
            double value = set ? c.getDouble("percent", 0) : c.getDouble("add", 0);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    PlayerData d = plugin.players().loaded(ctx.playerId());
                    Double current = d != null ? d.rolloverOverride : plugin.db().sync(conn -> {
                        try (var ps = conn.prepareStatement("SELECT rollover_override FROM players WHERE uuid=?")) {
                            ps.setString(1, ctx.playerId().toString());
                            try (var rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    double v = rs.getDouble(1);
                                    return rs.wasNull() ? null : v;
                                }
                            }
                        }
                        return null;
                    });
                    double base = current != null ? current : plugin.configs().main().getDouble("points.rollover-percent", 0);
                    double result = Math.max(0, Math.min(100, set ? value : base + value));
                    if (d != null) d.rolloverOverride = result;
                    plugin.playerStore().set(ctx.playerId(), "rollover_override", result);
                    return true;
                }

                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("head-protection", "charges", HeadProtection::new));
        m.registerType(Types.type("extra-heads", "amount", ExtraHeads::new));
        m.registerType(Types.type("bounty", "points", c -> new Bounty(c, plugin, c.path())));
        m.registerType(Types.type("shield", "charges", Shield::new));

        m.registerType(Types.type("flag", "flag", c -> {
            String flag = c.getString("flag", "");
            if (flag.isBlank()) throw new IllegalArgumentException("flag is required");
            boolean clear = c.getString("mode", "set").equalsIgnoreCase("clear");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    String f = ctx.format(flag);
                    ctx.data().put("had", plugin.players().hasFlag(ctx.playerId(), f));
                    plugin.players().setFlag(ctx.playerId(), f, !clear);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    Object had = ctx.data().get("had");
                    plugin.players().setFlag(ctx.playerId(), ctx.format(flag), Boolean.TRUE.equals(had));
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String stackKey() { return flag; }
                @Override public String describe(ActionContext ctx) { return (clear ? "cleared flag " : "flag ") + flag; }
            };
        }));

        m.registerType(Types.type("score-bonus", "amount", c -> {
            int amount = c.getInt("amount", 0);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    PlayerData d = plugin.players().loaded(ctx.playerId());
                    if (d != null) d.scoreBonus += amount;
                    plugin.playerStore().add(ctx.playerId(), "score_bonus", amount);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    PlayerData d = plugin.players().loaded(ctx.playerId());
                    if (d != null) d.scoreBonus -= amount;
                    plugin.playerStore().add(ctx.playerId(), "score_bonus", -amount);
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String describe(ActionContext ctx) { return (amount >= 0 ? "+" : "") + amount + " score at cycle end"; }
            };
        }));
    }

    private static String normalize(String mod) {
        String m = mod.trim();
        if (m.isEmpty()) return "+0";
        char c = m.charAt(0);
        if (c == '+' || c == '-' || c == '=') return m;
        return "+" + m;
    }
}
