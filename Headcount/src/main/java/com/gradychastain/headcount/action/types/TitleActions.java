package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;

import java.util.ArrayList;
import java.util.List;

/** grant-title, revoke-title, strip-title, force-title (Headcount titles from titles.yml). */
final class TitleActions {
    private TitleActions() {}

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("grant-title", "title", c -> {
            String title = c.getString("title", "");
            if (title.isBlank()) throw new IllegalArgumentException("title is required");
            boolean equip = c.getBool("equip", false);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    boolean had = plugin.titles().owns(ctx.playerId(), title);
                    ctx.data().put("had", had);
                    plugin.titles().grant(ctx.playerId(), title, "action:" + ctx.source());
                    if (equip) plugin.titles().equip(ctx.playerId(), title, true);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    if (!Boolean.TRUE.equals(ctx.data().get("had"))) plugin.titles().revoke(ctx.playerId(), title, "revert:" + ctx.source());
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String stackKey() { return title; }
                @Override public String describe(ActionContext ctx) { return "title " + title; }
            };
        }));

        m.registerType(Types.type("revoke-title", "title", c -> {
            String title = c.getString("title", "");
            if (title.isBlank()) throw new IllegalArgumentException("title is required");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    boolean had = plugin.titles().owns(ctx.playerId(), title);
                    ctx.data().put("had", had);
                    ctx.data().put("was-equipped", title.equalsIgnoreCase(plugin.titles().equipped(ctx.playerId())));
                    if (had) plugin.titles().revoke(ctx.playerId(), title, "action:" + ctx.source());
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    if (Boolean.TRUE.equals(ctx.data().get("had"))) {
                        plugin.titles().grant(ctx.playerId(), title, "revert:" + ctx.source());
                        if (Boolean.TRUE.equals(ctx.data().get("was-equipped"))) plugin.titles().equip(ctx.playerId(), title, true);
                    }
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("strip-title", "which", c -> {
            boolean all = c.getString("which", "equipped").equalsIgnoreCase("all");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    String equipped = plugin.titles().equipped(ctx.playerId());
                    if (equipped != null) ctx.data().put("equipped", equipped);
                    if (all) {
                        List<String> owned = new ArrayList<>(plugin.titles().owned(ctx.playerId()));
                        ctx.data().put("owned", owned);
                        for (String t : owned) plugin.titles().revoke(ctx.playerId(), t, "action:" + ctx.source());
                    } else if (equipped != null) {
                        plugin.titles().unequip(ctx.playerId());
                    }
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    if (ctx.data().get("owned") instanceof List<?> owned) {
                        for (Object t : owned) plugin.titles().grant(ctx.playerId(), String.valueOf(t), "revert:" + ctx.source());
                    }
                    Object eq = ctx.data().get("equipped");
                    if (eq != null && plugin.titles().owns(ctx.playerId(), String.valueOf(eq))) plugin.titles().equip(ctx.playerId(), String.valueOf(eq), true);
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("force-title", "title", c -> {
            String title = c.getString("title", "");
            if (title.isBlank()) throw new IllegalArgumentException("title is required");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    String prev = plugin.titles().equipped(ctx.playerId());
                    if (prev != null) ctx.data().put("previous", prev);
                    plugin.titles().force(ctx.playerId(), title, ctx.instanceId());
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    plugin.titles().force(ctx.playerId(), title, ctx.instanceId());
                }

                @Override
                public void revert(ActionContext ctx) {
                    plugin.titles().unforce(ctx.playerId(), ctx.instanceId());
                    Object prev = ctx.data().get("previous");
                    if (prev != null && plugin.titles().owns(ctx.playerId(), String.valueOf(prev))) plugin.titles().equip(ctx.playerId(), String.valueOf(prev), true);
                    else plugin.titles().unequip(ctx.playerId());
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean stateful() { return true; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String stackKey() { return "forced"; }
                @Override public String describe(ActionContext ctx) { return "forced title " + title; }
            };
        }));
    }
}
