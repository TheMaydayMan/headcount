package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionContextImpl;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.ConfigView;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.api.action.ActionType;
import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Helpers shared by the built-in action types. */
public final class Types {
    private Types() {}

    /** Registers every built-in action type. */
    public static void registerAll(ActionManager m, HeadcountPlugin plugin) {
        MessageActions.register(m, plugin);
        CommandActions.register(m, plugin);
        ItemActions.register(m, plugin);
        CurrencyActions.register(m, plugin);
        PermissionActions.register(m, plugin);
        TitleActions.register(m, plugin);
        ModerationActions.register(m, plugin);
        PvPActions.register(m, plugin);
        BuffActions.register(m, plugin);
        DenyAction.register(m, plugin);
        HeadcountActions.register(m, plugin);
        FlowActions.register(m, plugin);
    }

    static ActionType type(String id, String primary, Function<Cfg, ActionHandler> parser) {
        return new ActionType() {
            @Override public String id() { return id; }
            @Override public String primaryKey() { return primary; }
            @Override public ActionHandler parse(ConfigView settings) { return parser.apply((Cfg) settings); }
        };
    }

    static ActionContextImpl impl(ActionContext ctx) {
        return (ActionContextImpl) ctx;
    }

    /**
     * Who sees a message-like action: {@code player} (default with a target), {@code all} (default in global hooks),
     * {@code world}, {@code nearby:<radius>} or {@code permission:<node>}.
     */
    static List<Player> audience(Cfg c, ActionContext ctx) {
        String a = c.getString("audience", ctx.playerId() == null ? "all" : "player").trim().toLowerCase(Locale.ROOT);
        Player target = ctx.player();
        List<Player> out = new ArrayList<>();
        if (a.equals("player") || a.equals("self")) {
            if (target != null) out.add(target);
        } else if (a.equals("all") || a.equals("everyone") || a.equals("server")) {
            out.addAll(Bukkit.getOnlinePlayers());
        } else if (a.equals("world")) {
            if (target != null) out.addAll(target.getWorld().getPlayers());
        } else if (a.startsWith("nearby")) {
            double r = 32;
            int colon = a.indexOf(':');
            if (colon > 0) {
                try {
                    r = Double.parseDouble(a.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    // default radius
                }
            }
            if (target != null) {
                for (Player p : target.getWorld().getPlayers()) if (p.getLocation().distanceSquared(target.getLocation()) <= r * r) out.add(p);
            }
        } else if (a.startsWith("permission:")) {
            String node = a.substring("permission:".length());
            for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission(node)) out.add(p);
        } else if (target != null) {
            out.add(target);
        }
        return out;
    }

    /** True when the audience setting means other players (so the action also works without a target). */
    static boolean globalAudience(Cfg c) {
        String a = c.getString("audience", "player").toLowerCase(Locale.ROOT);
        return a.equals("all") || a.equals("everyone") || a.equals("server") || a.startsWith("permission:");
    }

    static NamespacedKey key(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = k.contains(":") ? NamespacedKey.fromString(k) : NamespacedKey.minecraft(k);
        if (key == null) throw new IllegalArgumentException("invalid key '" + s + "'");
        return key;
    }

    static List<String> lines(Cfg c, String key) {
        return c.getStringList(key);
    }
}
