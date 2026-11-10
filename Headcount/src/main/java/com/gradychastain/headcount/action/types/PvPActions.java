package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.integration.pvp.PvPHook;
import org.bukkit.entity.Player;

/** pvp: force PvP on or off through the PvP plugin, optionally locked so the player cannot toggle it. */
final class PvPActions {
    private PvPActions() {}

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("pvp", "state", c -> {
            String stateRaw = c.getString("state", "on").trim().toLowerCase(java.util.Locale.ROOT);
            boolean state = stateRaw.equals("on") || stateRaw.equals("true") || stateRaw.equals("enabled");
            boolean lock = c.getBool("lock", true);
            boolean restore = c.getBool("restore", true);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    PvPHook hook = plugin.integrations().pvp();
                    Player p = ctx.player();
                    if (hook == null) {
                        plugin.getLogger().warning("pvp action skipped: no PvP plugin is hooked (integrations.yml pvp.provider).");
                        return false;
                    }
                    if (p == null) return false;
                    Boolean before = hook.pvpEnabled(p);
                    if (before != null) ctx.data().put("previous", before);
                    hook.setPvP(p, state);
                    if (lock && ctx.timed()) hook.lock(p, state);
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    PvPHook hook = plugin.integrations().pvp();
                    Player p = ctx.player();
                    if (hook == null || p == null || !lock) return;
                    hook.lock(p, state);
                    Boolean now = hook.pvpEnabled(p);
                    if (now != null && now != state) hook.setPvP(p, state);
                }

                @Override
                public void revert(ActionContext ctx) {
                    PvPHook hook = plugin.integrations().pvp();
                    Player p = ctx.player();
                    if (hook == null || p == null) return;
                    hook.lock(p, null);
                    if (restore && ctx.data().get("previous") instanceof Boolean prev) hook.setPvP(p, prev);
                }

                @Override public boolean revertible() { return true; }
                @Override public String stackKey() { return "pvp"; }
                @Override public String describe(ActionContext ctx) { return "PvP forced " + (state ? "on" : "off") + (lock ? " (locked)" : ""); }
            };
        }));
    }
}
