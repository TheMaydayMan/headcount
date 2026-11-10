package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionList;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.util.Cfg;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** run (another action list) and random (one weighted choice). Their actions keep their own options. */
final class FlowActions {
    private FlowActions() {}

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("run", "list", c -> {
            String name = c.getString("list", "");
            Object inline = c.get("actions");
            return new ActionHandler() {
                ActionList resolve() {
                    if (inline != null) return plugin.actions().parseList(inline, c.path() + ".actions");
                    ActionList l = plugin.actions().named(name);
                    if (l == null) plugin.getLogger().warning("run: no action list named '" + name + "' in actions.yml");
                    return l;
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    ActionList l = resolve();
                    if (l == null) return false;
                    plugin.actions().run(l, ctx.playerId(), Types.impl(ctx).vars(), ctx.source());
                    return true;
                }

                @Override public boolean needsPlayer() { return false; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("random", "choices", c -> {
            record Choice(double weight, Object raw, String path) {}
            List<Choice> choices = new ArrayList<>();
            List<Object> raw = c.getList("choices");
            for (int i = 0; i < raw.size(); i++) {
                Cfg ch = Cfg.of(raw.get(i));
                double w = ch.getDouble("weight", 1);
                if (w <= 0) continue;
                Object body = ch.has("actions") ? ch.get("actions") : ch.has("list") ? ch.getString("list") : null;
                if (body == null) throw new IllegalArgumentException("each random choice needs 'actions' or 'list'");
                choices.add(new Choice(w, body, c.path() + ".choices[" + i + "]"));
            }
            if (choices.isEmpty()) throw new IllegalArgumentException("random needs at least one choice with a positive weight");
            double total = choices.stream().mapToDouble(Choice::weight).sum();
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    double roll = ThreadLocalRandom.current().nextDouble(total);
                    for (Choice ch : choices) {
                        roll -= ch.weight();
                        if (roll < 0) {
                            ActionList l = plugin.actions().parseList(ch.raw(), ch.path());
                            plugin.actions().run(l, ctx.playerId(), Types.impl(ctx).vars(), ctx.source());
                            return true;
                        }
                    }
                    return true;
                }

                @Override public boolean needsPlayer() { return false; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));
    }
}
