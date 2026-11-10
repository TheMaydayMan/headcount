package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.text.Vars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * console and player-command. Commands may use {@code <player>}, {@code <uuid>} and every context variable.
 * {@code revert-commands} run when a timed action ends.
 */
final class CommandActions {
    private CommandActions() {}

    private static String clean(String cmd) {
        String c = cmd.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }

    private static Vars vars(ActionContext ctx) {
        Vars v = Types.impl(ctx).vars().copy();
        if (ctx.playerId() != null) {
            v.put("player", Types.impl(ctx).playerName());
            v.put("uuid", ctx.playerId().toString());
        }
        return v;
    }

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("console", "command", c -> {
            List<String> commands = c.has("commands") ? c.getStringList("commands") : c.getStringList("command");
            List<String> revert = c.getStringList("revert-commands");
            if (commands.isEmpty()) throw new IllegalArgumentException("console needs 'command' or 'commands'");
            boolean usesPlayer = commands.stream().anyMatch(s -> s.contains("<player>") || s.contains("<uuid>"))
                    || revert.stream().anyMatch(s -> s.contains("<player>") || s.contains("<uuid>"));
            return new ActionHandler() {
                void run(List<String> list, ActionContext ctx) {
                    Vars v = vars(ctx);
                    for (String cmd : list) {
                        String resolved = clean(plugin.text().plain(cmd, ctx.offlinePlayer(), v));
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                        } catch (RuntimeException e) {
                            plugin.getLogger().warning("Console command '" + resolved + "' failed: " + e.getMessage());
                        }
                    }
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    run(commands, ctx);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    run(revert, ctx);
                }

                @Override public boolean revertible() { return !revert.isEmpty(); }
                @Override public boolean requiresOnline() { return c.getBool("require-online", false); }
                @Override public boolean needsPlayer() { return usesPlayer; }
            };
        }));

        m.registerType(Types.type("player-command", "command", c -> {
            List<String> commands = c.has("commands") ? c.getStringList("commands") : c.getStringList("command");
            List<String> revert = c.getStringList("revert-commands");
            if (commands.isEmpty()) throw new IllegalArgumentException("player-command needs 'command' or 'commands'");
            return new ActionHandler() {
                void run(List<String> list, ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return;
                    Vars v = vars(ctx);
                    for (String cmd : list) p.performCommand(clean(plugin.text().plain(cmd, p, v)));
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    if (ctx.player() == null) return false;
                    run(commands, ctx);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    run(revert, ctx);
                }

                @Override public boolean revertible() { return !revert.isEmpty(); }
            };
        }));
    }
}
