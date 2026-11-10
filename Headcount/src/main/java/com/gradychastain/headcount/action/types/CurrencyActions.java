package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.integration.economy.EconomyHook;

/** points (Headcount's currency, now or for the next cycle) and money (through Vault). */
final class CurrencyActions {
    private CurrencyActions() {}

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("points", "amount", c -> {
            String amountExpr = c.getString("amount", "0");
            boolean next = c.getString("when", "now").toLowerCase(java.util.Locale.ROOT).startsWith("next");
            boolean undo = c.getBool("revert", true);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    long amount;
                    try {
                        amount = Math.round(Double.parseDouble(ctx.format(amountExpr).trim()));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("points action amount '" + amountExpr + "' is not a number");
                        return false;
                    }
                    long applied = plugin.points().change(ctx.playerId(), amount, next, "action:" + ctx.source(), "action");
                    ctx.data().put("applied", applied);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    Object a = ctx.data().get("applied");
                    if (a instanceof Number n && n.longValue() != 0) plugin.points().change(ctx.playerId(), -n.longValue(), next, "revert:" + ctx.source(), "action");
                }

                @Override public boolean revertible() { return undo; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String describe(ActionContext ctx) { return amountExpr + " points" + (next ? " next cycle" : ""); }
            };
        }));

        m.registerType(Types.type("money", "amount", c -> {
            String amountExpr = c.getString("amount", "0");
            boolean undo = c.getBool("revert", true);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    EconomyHook eco = plugin.integrations().economy();
                    if (eco == null) {
                        plugin.getLogger().warning("money action skipped: no Vault economy is installed.");
                        return false;
                    }
                    double amount;
                    try {
                        amount = Double.parseDouble(ctx.format(amountExpr).trim());
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("money action amount '" + amountExpr + "' is not a number");
                        return false;
                    }
                    boolean ok = amount >= 0 ? eco.deposit(ctx.offlinePlayer(), amount) : eco.withdraw(ctx.offlinePlayer(), -amount);
                    if (ok) ctx.data().put("applied", amount);
                    return ok;
                }

                @Override
                public void revert(ActionContext ctx) {
                    EconomyHook eco = plugin.integrations().economy();
                    Object a = ctx.data().get("applied");
                    if (eco == null || !(a instanceof Number n)) return;
                    double amt = n.doubleValue();
                    if (amt >= 0) eco.withdraw(ctx.offlinePlayer(), amt);
                    else eco.deposit(ctx.offlinePlayer(), -amt);
                }

                @Override public boolean revertible() { return undo; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));
    }
}
