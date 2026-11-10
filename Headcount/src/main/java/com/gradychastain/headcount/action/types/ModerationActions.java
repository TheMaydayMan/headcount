package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.action.DurationSpec;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.integration.moderation.ModerationHook;
import com.gradychastain.headcount.integration.moderation.ModerationHook.Kind;
import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/**
 * ban, mute, kick, warn, jail through the configured moderation plugin (integrations.yml), falling back to vanilla
 * bans and Headcount's own mutes and jails. A timed duration makes the punishment temporary; {@code instant} or
 * {@code permanent} make it permanent. Cycle- and death-bound durations are lifted by Headcount when they end.
 */
final class ModerationActions {
    private ModerationActions() {}

    /** The duration to hand the moderation plugin: the timed length, the time left in the cycle, or null (permanent). */
    static Duration punishmentDuration(HeadcountPlugin plugin, ActionContext ctx) {
        DurationSpec d = Types.impl(ctx).durationSpec();
        return switch (d.kind()) {
            case TIME -> Duration.ofMillis(d.millis());
            case CYCLE, CYCLES -> {
                var c = plugin.cycles().current();
                long left = c == null ? 0 : c.remainingMs();
                // For several cycles the exact end is unknown; Headcount lifts it when the last cycle ends.
                yield d.kind() == DurationSpec.Kind.CYCLE && left > 0 ? Duration.ofMillis(left) : null;
            }
            default -> null;
        };
    }

    static Location jailLocation(HeadcountPlugin plugin) {
        String raw = plugin.configs().integrations().getString("moderation.vanilla-jail.location", "");
        String[] p = raw.split(",");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0].trim());
        if (w == null) return null;
        try {
            Location l = new Location(w, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim()));
            if (p.length >= 6) {
                l.setYaw(Float.parseFloat(p[4].trim()));
                l.setPitch(Float.parseFloat(p[5].trim()));
            }
            return l;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void register(ActionManager m, HeadcountPlugin plugin) {
        for (Kind kind : Kind.values()) {
            String id = kind.name().toLowerCase(Locale.ROOT);
            m.registerType(Types.type(id, "reason", c -> handler(plugin, kind, c)));
        }
    }

    private static ActionHandler handler(HeadcountPlugin plugin, Kind kind, Cfg c) {
        String reason = c.getString("reason", "");
        String jail = c.getString("jail", "jail");
        boolean liftable = kind == Kind.BAN || kind == Kind.MUTE || kind == Kind.JAIL;
        return new ActionHandler() {
            ModerationHook hook() {
                return plugin.integrations().moderation(kind);
            }

            @Override
            public boolean apply(ActionContext ctx) {
                ModerationHook h = hook();
                if (h == null) return false;
                // A kick for someone offline is skipped rather than saved for their next join.
                if (kind == Kind.KICK && ctx.player() == null) return false;
                String r = ctx.format(reason);
                Duration d = punishmentDuration(plugin, ctx);
                if (kind == Kind.JAIL && h.enforcedByHeadcount(Kind.JAIL)) {
                    Player p = ctx.player();
                    Location jailAt = jailLocation(plugin);
                    if (p == null) return false;
                    if (jailAt == null) {
                        plugin.getLogger().warning("jail action: set moderation.vanilla-jail.location in integrations.yml (world,x,y,z).");
                        return false;
                    }
                    Location from = p.getLocation();
                    ctx.data().put("return", from.getWorld().getName() + "," + from.getX() + "," + from.getY() + "," + from.getZ() + "," + from.getYaw() + "," + from.getPitch());
                    p.teleport(jailAt);
                    plugin.lang().send(p, "moderation.jailed", "reason", r);
                    return true;
                }
                return h.apply(kind, ctx.playerId(), Types.impl(ctx).playerName(), r, d, jail);
            }

            @Override
            public void maintain(ActionContext ctx) {
                if (kind != Kind.JAIL || !hook().enforcedByHeadcount(Kind.JAIL)) return;
                Player p = ctx.player();
                Location jailAt = jailLocation(plugin);
                if (p == null || jailAt == null) return;
                double radius = plugin.configs().integrations().getDouble("moderation.vanilla-jail.radius", 8);
                if (!p.getWorld().equals(jailAt.getWorld()) || p.getLocation().distanceSquared(jailAt) > radius * radius) p.teleport(jailAt);
            }

            @Override
            public void revert(ActionContext ctx) {
                ModerationHook h = hook();
                if (h == null) return;
                if (kind == Kind.JAIL && h.enforcedByHeadcount(Kind.JAIL)) {
                    Player p = ctx.player();
                    if (p == null) return;
                    String release = plugin.configs().integrations().getString("moderation.vanilla-jail.release-to", "previous");
                    Location back = null;
                    if (release.equalsIgnoreCase("previous") && ctx.data().get("return") instanceof String s) {
                        String[] parts = s.split(",");
                        World w = Bukkit.getWorld(parts[0]);
                        if (w != null && parts.length >= 6) back = new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
                    }
                    if (back == null) back = p.getWorld().getSpawnLocation();
                    p.teleport(back);
                    plugin.lang().send(p, "moderation.released");
                    return;
                }
                if (kind == Kind.MUTE && h.enforcedByHeadcount(Kind.MUTE)) {
                    Player p = ctx.player();
                    if (p != null) plugin.lang().send(p, "moderation.unmuted");
                    return;
                }
                h.revoke(kind, ctx.playerId(), Types.impl(ctx).playerName());
            }

            @Override public boolean revertible() { return liftable; }
            @Override public boolean requiresOnline() { return kind == Kind.JAIL; }
            /** Headcount's own mutes and jails exist only while tracked, so "permanent" keeps them tracked. */
            @Override public boolean stateful() { return kind == Kind.MUTE || kind == Kind.JAIL; }
            @Override public String stackKey() { return kind.name(); }
            @Override public String describe(ActionContext ctx) { return kind.name().toLowerCase(Locale.ROOT) + (reason.isBlank() ? "" : ": " + ctx.format(reason)); }
        };
    }
}
