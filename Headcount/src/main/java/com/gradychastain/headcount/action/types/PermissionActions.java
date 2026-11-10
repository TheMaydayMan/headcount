package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.integration.permission.PermissionHook;

import java.time.Duration;
import java.util.Locale;

/**
 * permission, group, primary-group, track, prefix, suffix. With LuckPerms, timed changes are given LuckPerms' own
 * expiry as well, so they end even if Headcount is removed; Headcount still reverts them when the action ends.
 */
final class PermissionActions {
    private PermissionActions() {}

    private static PermissionHook hook(HeadcountPlugin plugin, String action) {
        PermissionHook h = plugin.integrations().permissions();
        if (h == null) plugin.getLogger().warning(action + " action skipped: no permissions plugin (LuckPerms or Vault) is installed.");
        return h;
    }

    private static Duration expiry(ActionContext ctx) {
        return ctx.duration();
    }

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("permission", "permission", c -> {
            String node = c.getString("permission", "");
            if (node.isBlank()) throw new IllegalArgumentException("permission is required");
            boolean remove = c.getString("mode", "add").equalsIgnoreCase("remove");
            boolean value = c.getBool("value", true);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    PermissionHook h = hook(plugin, "permission");
                    if (h == null) return false;
                    String n = ctx.format(node);
                    if (remove) h.unsetPermission(ctx.playerId(), n);
                    else h.setPermission(ctx.playerId(), n, value, expiry(ctx));
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    PermissionHook h = plugin.integrations().permissions();
                    if (h == null) return;
                    String n = ctx.format(node);
                    if (remove) h.setPermission(ctx.playerId(), n, value, null);
                    else h.unsetPermission(ctx.playerId(), n);
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String stackKey() { return node; }
                @Override public String describe(ActionContext ctx) { return (remove ? "lost " : "") + "permission " + node; }
            };
        }));

        m.registerType(Types.type("group", "group", c -> {
            String group = c.getString("group", "");
            if (group.isBlank()) throw new IllegalArgumentException("group is required");
            boolean remove = c.getString("mode", "add").equalsIgnoreCase("remove");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    PermissionHook h = hook(plugin, "group");
                    if (h == null) return false;
                    if (remove) h.removeGroup(ctx.playerId(), group);
                    else h.addGroup(ctx.playerId(), group, expiry(ctx));
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    PermissionHook h = plugin.integrations().permissions();
                    if (h == null) return;
                    if (remove) h.addGroup(ctx.playerId(), group, null);
                    else h.removeGroup(ctx.playerId(), group);
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String stackKey() { return group; }
                @Override public String describe(ActionContext ctx) { return (remove ? "removed from " : "in ") + "group " + group; }
            };
        }));

        m.registerType(Types.type("primary-group", "group", c -> {
            String group = c.getString("group", "");
            if (group.isBlank()) throw new IllegalArgumentException("group is required");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    PermissionHook h = hook(plugin, "primary-group");
                    if (h == null) return false;
                    String previous = h.setPrimaryGroup(ctx.playerId(), group);
                    if (previous != null) ctx.data().put("previous", previous);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    PermissionHook h = plugin.integrations().permissions();
                    Object prev = ctx.data().get("previous");
                    if (h == null || prev == null) return;
                    h.setPrimaryGroup(ctx.playerId(), String.valueOf(prev));
                    if (!String.valueOf(prev).equalsIgnoreCase(group)) h.removeGroup(ctx.playerId(), group);
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String stackKey() { return "primary"; }
            };
        }));

        m.registerType(Types.type("track", "track", c -> {
            String track = c.getString("track", "");
            if (track.isBlank()) throw new IllegalArgumentException("track is required");
            boolean demote = c.getString("direction", "promote").toLowerCase(Locale.ROOT).startsWith("demote");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    PermissionHook h = hook(plugin, "track");
                    if (h == null) return false;
                    if (!h.supportsTracks()) {
                        plugin.getLogger().warning("track action needs LuckPerms; " + h.name() + " has no tracks.");
                        return false;
                    }
                    return demote ? h.demote(ctx.playerId(), track) : h.promote(ctx.playerId(), track);
                }

                @Override
                public void revert(ActionContext ctx) {
                    PermissionHook h = plugin.integrations().permissions();
                    if (h == null || !h.supportsTracks()) return;
                    if (demote) h.promote(ctx.playerId(), track);
                    else h.demote(ctx.playerId(), track);
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));

        for (String kind : new String[]{"prefix", "suffix"}) {
            m.registerType(Types.type(kind, "text", c -> {
                String text = c.getString("text", "");
                int priority = c.getInt("priority", 100);
                boolean suffix = kind.equals("suffix");
                return new ActionHandler() {
                    @Override
                    public boolean apply(ActionContext ctx) {
                        PermissionHook h = hook(plugin, kind);
                        if (h == null) return false;
                        String t = ctx.format(text);
                        ctx.data().put("text", t);
                        if (suffix) h.addSuffix(ctx.playerId(), t, priority, expiry(ctx));
                        else h.addPrefix(ctx.playerId(), t, priority, expiry(ctx));
                        return true;
                    }

                    @Override
                    public void revert(ActionContext ctx) {
                        PermissionHook h = plugin.integrations().permissions();
                        if (h == null) return;
                        String t = String.valueOf(ctx.data().getOrDefault("text", text));
                        if (suffix) h.removeSuffix(ctx.playerId(), t, priority);
                        else h.removePrefix(ctx.playerId(), t, priority);
                    }

                    @Override public boolean revertible() { return true; }
                    @Override public boolean requiresOnline() { return false; }
                    @Override public String stackKey() { return kind + ":" + priority; }
                    @Override public String describe(ActionContext ctx) { return kind + " " + text; }
                };
            }));
        }
    }
}
