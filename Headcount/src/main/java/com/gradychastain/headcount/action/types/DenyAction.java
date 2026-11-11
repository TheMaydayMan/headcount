package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.util.Cfg;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * deny: blocks things for the duration. Categories: break, place, chat, commands, items, fly, elytra, ender-pearl,
 * worlds, trade, shop, vault, interact, drop, pickup, attack. {@code commands}, {@code items} and {@code worlds}
 * narrow the matching categories; {@code allowed-commands} are always allowed.
 */
public final class DenyAction {
    public static final Set<String> CATEGORIES = Set.of("break", "place", "chat", "commands", "items", "fly", "elytra", "ender-pearl",
            "worlds", "trade", "shop", "vault", "interact", "drop", "pickup", "attack");

    private DenyAction() {}

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("deny", "deny", Handler::new));
    }

    /** The handler; public so the deny listener can query it. */
    public static final class Handler implements ActionHandler {
        private final Set<String> categories = new HashSet<>();
        private final Set<String> commands = new HashSet<>();
        private final Set<String> allowedCommands = new HashSet<>();
        private final Set<String> items = new HashSet<>();
        private final Set<String> worlds = new HashSet<>();
        private final String message;
        private static final Map<String, Long> lastMessage = new HashMap<>();

        Handler(Cfg c) {
            for (String s : c.getStringList("deny")) {
                String k = s.trim().toLowerCase(Locale.ROOT);
                if (!CATEGORIES.contains(k)) throw new IllegalArgumentException("unknown deny category '" + s + "' (use " + String.join(", ", CATEGORIES) + ")");
                categories.add(k);
            }
            if (categories.isEmpty()) throw new IllegalArgumentException("deny needs a list of categories");
            for (String s : c.getStringList("commands")) commands.add(normalize(s));
            for (String s : c.getStringList("allowed-commands")) allowedCommands.add(normalize(s));
            for (String s : c.getStringList("items")) items.add(s.trim().toUpperCase(Locale.ROOT));
            for (String s : c.getStringList("worlds")) worlds.add(s.trim().toLowerCase(Locale.ROOT));
            message = c.getString("message", null);
        }

        public static String normalize(String cmd) {
            String c = cmd.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("/")) c = c.substring(1);
            int space = c.indexOf(' ');
            if (space >= 0) c = c.substring(0, space);
            int colon = c.indexOf(':');
            if (colon >= 0) c = c.substring(colon + 1);
            return c;
        }

        public boolean denies(String category, String detail) {
            if (!categories.contains(category)) return false;
            String d = detail == null ? null : detail.toLowerCase(Locale.ROOT);
            return switch (category) {
                case "commands" -> {
                    String label = d == null ? "" : normalize(d);
                    if (allowedCommands.contains(label)) yield false;
                    yield commands.isEmpty() || commands.contains(label);
                }
                case "items" -> items.isEmpty() || (detail != null && items.contains(detail.toUpperCase(Locale.ROOT)));
                case "worlds" -> d != null && worlds.contains(d);
                default -> true;
            };
        }

        public void notify(HeadcountPlugin plugin, Player p, String category, ActionContext ctx) {
            // At most one message per category every 1.5 s (break and place fire many events), but a different
            // denied thing is explained right away.
            long now = System.currentTimeMillis();
            String key = p.getUniqueId() + ":" + category;
            Long last = lastMessage.get(key);
            if (last != null && now - last < 1500) return;
            if (lastMessage.size() > 512) lastMessage.values().removeIf(t -> now - t >= 1500);
            lastMessage.put(key, now);
            if (message != null) plugin.lang().sendTemplate(p, message, com.gradychastain.headcount.text.Vars.of("category", category));
            else plugin.lang().send(p, "deny." + category, com.gradychastain.headcount.text.Vars.of("category", category));
        }

        @Override
        public boolean apply(ActionContext ctx) {
            Player p = ctx.player();
            if (p != null && categories.contains("fly") && p.isFlying()) p.setFlying(false);
            if (p != null && categories.contains("elytra") && p.isGliding()) p.setGliding(false);
            return true;
        }

        @Override
        public void maintain(ActionContext ctx) {
            Player p = ctx.player();
            if (p == null) return;
            if (categories.contains("fly") && p.isFlying()) p.setFlying(false);
            if (categories.contains("elytra") && p.isGliding()) p.setGliding(false);
        }

        @Override public boolean stateful() { return true; }
        @Override public boolean requiresOnline() { return false; }
        @Override public String describe(ActionContext ctx) { return Types.describe("deny", "categories", String.join(", ", categories)); }

        public Set<String> categories() {
            return categories;
        }

        public List<String> worldList() {
            return List.copyOf(worlds);
        }
    }
}
