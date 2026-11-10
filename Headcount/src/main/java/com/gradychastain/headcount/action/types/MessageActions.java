package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.integration.discord.DiscordHook;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Durations;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** message, title, actionbar, sound, particle, bossbar, broadcast, discord, broadcast-location. */
final class MessageActions {
    private MessageActions() {}

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("message", "text", c -> {
            List<String> lines = c.getStringList("text");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    for (Player p : Types.audience(c, ctx)) {
                        for (String line : lines) {
                            Component msg = plugin.text().render(line, ctx.playerId() == null ? p : ctx.offlinePlayer(), Types.impl(ctx).vars());
                            p.sendMessage(msg);
                        }
                    }
                    return true;
                }
                @Override public boolean needsPlayer() { return false; } // with no target the audience is everyone
            };
        }));

        m.registerType(Types.type("broadcast", "text", c -> {
            List<String> lines = c.getStringList("text");
            String permission = c.getString("permission", null);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        if (permission != null && !p.hasPermission(permission)) continue;
                        for (String line : lines) p.sendMessage(plugin.text().render(line, ctx.playerId() == null ? p : ctx.offlinePlayer(), Types.impl(ctx).vars()));
                    }
                    if (c.getBool("console", true)) {
                        for (String line : lines) plugin.getServer().getConsoleSender().sendMessage(ctx.render(line));
                    }
                    return true;
                }
                @Override public boolean needsPlayer() { return false; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("title", "title", c -> {
            String title = c.getString("title", "");
            String subtitle = c.getString("subtitle", "");
            Title.Times times = Title.Times.times(Duration.ofMillis(c.getMillis("fade-in", 500)), Duration.ofMillis(c.getMillis("stay", 3000)),
                    Duration.ofMillis(c.getMillis("fade-out", 1000)));
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    for (Player p : Types.audience(c, ctx)) {
                        var subject = ctx.playerId() == null ? p : ctx.offlinePlayer();
                        p.showTitle(Title.title(plugin.text().render(title, subject, Types.impl(ctx).vars()),
                                plugin.text().render(subtitle, subject, Types.impl(ctx).vars()), times));
                    }
                    return true;
                }
                @Override public boolean needsPlayer() { return false; } // with no target the audience is everyone
            };
        }));

        m.registerType(Types.type("actionbar", "text", c -> {
            String text = c.getString("text", "");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    for (Player p : Types.audience(c, ctx)) {
                        p.sendActionBar(plugin.text().render(text, ctx.playerId() == null ? p : ctx.offlinePlayer(), Types.impl(ctx).vars()));
                    }
                    return true;
                }
                @Override public boolean needsPlayer() { return false; } // with no target the audience is everyone
                @Override public void repeat(ActionContext ctx) { apply(ctx); }
            };
        }));

        m.registerType(Types.type("sound", "sound", c -> {
            String name = c.getString("sound", "minecraft:entity.experience_orb.pickup").trim().toLowerCase(Locale.ROOT);
            if (!name.contains(".") && !name.contains(":")) {
                throw new IllegalArgumentException("sound '" + name + "' must be a sound key such as entity.experience_orb.pickup");
            }
            if (!Key.parseable(name.contains(":") ? name : "minecraft:" + name)) throw new IllegalArgumentException("invalid sound key '" + name + "'");
            Key key = Key.key(name.contains(":") ? name : "minecraft:" + name);
            float volume = (float) c.getDouble("volume", 1.0);
            float pitch = (float) c.getDouble("pitch", 1.0);
            Sound.Source source;
            try {
                source = Sound.Source.valueOf(c.getString("source", "master").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("sound source must be master, music, record, weather, block, hostile, neutral, player, ambient or voice");
            }
            Sound sound = Sound.sound(key, source, volume, pitch);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    for (Player p : Types.audience(c, ctx)) p.playSound(sound, net.kyori.adventure.sound.Sound.Emitter.self());
                    return true;
                }
                @Override public boolean needsPlayer() { return false; } // with no target the audience is everyone
            };
        }));

        m.registerType(Types.type("particle", "particle", c -> {
            String name = c.getString("particle", "happy_villager");
            Particle particle = Registry.PARTICLE_TYPE.get(Types.key(name));
            if (particle == null) throw new IllegalArgumentException("unknown particle '" + name + "'");
            if (particle.getDataType() != Void.class) throw new IllegalArgumentException("particle '" + name + "' needs extra data and is not supported here");
            int count = c.getInt("count", 20);
            double ox = c.getDouble("offset-x", c.getDouble("offset", 0.5)), oy = c.getDouble("offset-y", c.getDouble("offset", 0.5)),
                    oz = c.getDouble("offset-z", c.getDouble("offset", 0.5));
            double speed = c.getDouble("speed", 0.05);
            double height = c.getDouble("height", 1.0);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    Location l = p.getLocation().add(0, height, 0);
                    p.getWorld().spawnParticle(particle, l, count, ox, oy, oz, speed);
                    return true;
                }
            };
        }));

        m.registerType(Types.type("bossbar", "text", c -> {
            String text = c.getString("text", "");
            BossBar.Color color;
            BossBar.Overlay overlay;
            try {
                color = BossBar.Color.valueOf(c.getString("color", "red").toUpperCase(Locale.ROOT));
                overlay = BossBar.Overlay.valueOf(c.getString("overlay", "progress").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("bossbar color must be pink, blue, red, green, yellow, purple or white; overlay progress or notched_6/10/12/20");
            }
            float progress = (float) c.getDouble("progress", 1.0);
            String countdown = c.getString("countdown-to", null);
            long seconds = c.getMillis("seconds", 10_000);
            boolean global = Types.globalAudience(c);
            return new ActionHandler() {
                long target(ActionContext ctx) {
                    if (countdown == null) return 0;
                    Object stored = ctx.data().get("countdown_to");
                    if (stored instanceof Number n) return n.longValue();
                    long t = resolveCountdown(plugin, countdown);
                    ctx.data().put("countdown_to", t);
                    ctx.data().put("countdown_from", System.currentTimeMillis());
                    return t;
                }

                BossBars.Spec spec(ActionContext ctx) {
                    long to = target(ctx);
                    Object from = ctx.data().get("countdown_from");
                    return new BossBars.Spec(text, color, overlay, progress, to, from instanceof Number n ? n.longValue() : System.currentTimeMillis());
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    BossBars.Spec spec = spec(ctx);
                    if (ctx.playerId() == null || global && ctx.instanceId() == null) {
                        plugin.bossBars().showGlobal(spec, seconds);
                        return true;
                    }
                    Player p = ctx.player();
                    if (p == null) return false;
                    if (ctx.instanceId() == null) plugin.bossBars().flash(p, spec, spec.countdownTo() > 0 ? spec.countdownTo() - System.currentTimeMillis() : seconds);
                    else plugin.bossBars().showFor(ctx.instanceId(), p, spec);
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null || ctx.instanceId() == null) return;
                    if (!plugin.bossBars().hasPerPlayer(ctx.instanceId())) plugin.bossBars().showFor(ctx.instanceId(), p, spec(ctx));
                    else plugin.bossBars().update(ctx.instanceId(), p);
                }

                @Override
                public void revert(ActionContext ctx) {
                    plugin.bossBars().hide(ctx.instanceId(), ctx.player());
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean needsPlayer() { return false; } // with no target the audience is everyone
                @Override public void repeat(ActionContext ctx) { maintain(ctx); }
                @Override public String describe(ActionContext ctx) { return "boss bar"; }
            };
        }));

        m.registerType(Types.type("discord", "text", c -> {
            String channel = c.getString("channel", plugin.configs().integrations().getString("discord.default-channel", "global"));
            List<String> lines = c.getStringList("text");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    DiscordHook hook = plugin.integrations().discord();
                    if (hook == null) return false;
                    return hook.send(channel, String.join("\n", lines.stream().map(ctx::format).toList()));
                }
                @Override public boolean needsPlayer() { return false; }
                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("broadcast-location", "text", c -> {
            String text = c.getString("text", "<red><player></red> is at <yellow><x>, <y>, <z></yellow> in <world>!");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    Location l = p.getLocation();
                    var vars = Types.impl(ctx).vars().copy().put("player", p.getName()).put("x", l.getBlockX()).put("y", l.getBlockY())
                            .put("z", l.getBlockZ()).put("world", l.getWorld().getName());
                    Cfg audienceCfg = c.has("audience") ? c : c.with("audience", "all");
                    for (Player to : Types.audience(audienceCfg, ctx)) to.sendMessage(plugin.text().render(text, ctx.offlinePlayer(), vars));
                    return true;
                }
                @Override public String describe(ActionContext ctx) { return "your location is being broadcast"; }
            };
        }));
    }

    /** {@code cycle-end}, a duration from now ({@code 60s}), or a date-time ({@code 2026-12-01T00:00}) in the cycle time zone. */
    static long resolveCountdown(HeadcountPlugin plugin, String spec) {
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.equals("cycle-end") || s.equals("cycle")) {
            var c = plugin.cycles().current();
            return c == null ? System.currentTimeMillis() : c.endMs();
        }
        long ms = Durations.parseMillis(s);
        if (ms >= 0) return System.currentTimeMillis() + ms;
        try {
            return LocalDateTime.parse(spec.trim()).atZone(plugin.cycles().schedule().zone()).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return System.currentTimeMillis();
        }
    }
}
