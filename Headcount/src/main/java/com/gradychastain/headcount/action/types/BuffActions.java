package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * potion, attribute, glowing, fire, lightning, freeze, hunger, gamemode, teleport, heal, feed, damage, launch,
 * clear-effects. Timed effects are re-applied every second while active, so they survive death and milk.
 */
final class BuffActions {
    private BuffActions() {}

    static Location parseLocation(String raw) {
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

    static String encode(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
    }

    static Location randomSafe(World w, Location center, double minR, double maxR) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 25; i++) {
            double angle = r.nextDouble(Math.PI * 2);
            double dist = minR + r.nextDouble(Math.max(1, maxR - minR));
            int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
            if (!w.getWorldBorder().isInside(new Location(w, x, 0, z))) continue;
            Block top = w.getHighestBlockAt(x, z);
            Material m = top.getType();
            if (m == Material.WATER || m == Material.LAVA || m.isAir() || m == Material.MAGMA_BLOCK || m == Material.CACTUS) continue;
            return new Location(w, x + 0.5, top.getY() + 1, z + 0.5);
        }
        return null;
    }

    static void register(ActionManager m, HeadcountPlugin plugin) {
        m.registerType(Types.type("potion", "effect", c -> {
            String effect = c.getString("effect", "");
            PotionEffectType type = Registry.MOB_EFFECT.get(Types.key(effect));
            if (type == null) throw new IllegalArgumentException("unknown potion effect '" + effect + "'");
            int amplifier = Math.max(0, c.getInt("amplifier", 0));
            int seconds = Math.max(1, (int) (c.getMillis("seconds", 30_000) / 1000));
            boolean ambient = c.getBool("ambient", false), particles = c.getBool("particles", true), icon = c.getBool("icon", true);
            return new ActionHandler() {
                PotionEffect effect(ActionContext ctx) {
                    int ticks = ctx.timed() ? PotionEffect.INFINITE_DURATION : seconds * 20;
                    return new PotionEffect(type, ticks, amplifier, ambient, particles, icon);
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    p.addPotionEffect(effect(ctx));
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null || p.isDead()) return;
                    PotionEffect cur = p.getPotionEffect(type);
                    if (cur == null || cur.getAmplifier() < amplifier || (!cur.isInfinite() && cur.getDuration() < 40)) p.addPotionEffect(effect(ctx));
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return;
                    PotionEffect cur = p.getPotionEffect(type);
                    if (cur != null && cur.isInfinite() && cur.getAmplifier() == amplifier) p.removePotionEffect(type);
                }

                @Override public boolean revertible() { return true; }
                @Override public String stackKey() { return type.getKey().getKey(); }
                @Override public String describe(ActionContext ctx) { return Types.describe("potion", "effect", type.getKey().getKey().replace('_', ' '), "level", amplifier + 1); }
            };
        }));

        m.registerType(Types.type("attribute", "attribute", c -> {
            String name = c.getString("attribute", "");
            Attribute attr = Registry.ATTRIBUTE.get(Types.key(name));
            if (attr == null) throw new IllegalArgumentException("unknown attribute '" + name + "' (e.g. max_health, movement_speed, attack_damage, scale, jump_strength, gravity, block_break_speed)");
            double amount = c.getDouble("amount", 0);
            AttributeModifier.Operation op;
            String opName = c.getString("operation", "add_number").toLowerCase(Locale.ROOT);
            op = switch (opName) {
                case "add_number", "add", "add_value" -> AttributeModifier.Operation.ADD_NUMBER;
                case "add_scalar", "add_multiplied_base", "percent" -> AttributeModifier.Operation.ADD_SCALAR;
                case "multiply_scalar_1", "add_multiplied_total", "multiply" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                default -> throw new IllegalArgumentException("operation must be add_number, add_scalar or multiply_scalar_1");
            };
            return new ActionHandler() {
                NamespacedKey key(ActionContext ctx) {
                    Object stored = ctx.data().get("key");
                    String k = stored != null ? String.valueOf(stored)
                            : "action_" + (ctx.instanceId() != null ? ctx.instanceId().replace("-", "") : UUID.randomUUID().toString().replace("-", ""));
                    ctx.data().put("key", k);
                    return new NamespacedKey(plugin, k);
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    AttributeInstance inst = p.getAttribute(attr);
                    if (inst == null) return false;
                    NamespacedKey k = key(ctx);
                    inst.removeModifier(k);
                    AttributeModifier mod = new AttributeModifier(k, amount, op);
                    if (ctx.timed()) inst.addTransientModifier(mod);
                    else inst.addModifier(mod); // instant/permanent: stays on the player
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return;
                    AttributeInstance inst = p.getAttribute(attr);
                    if (inst == null) return;
                    NamespacedKey k = key(ctx);
                    if (inst.getModifier(k) == null) inst.addTransientModifier(new AttributeModifier(k, amount, op));
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return;
                    AttributeInstance inst = p.getAttribute(attr);
                    if (inst != null) inst.removeModifier(key(ctx));
                }

                @Override public boolean revertible() { return true; }
                @Override public String stackKey() { return attr.getKey().getKey(); }
                @Override public String describe(ActionContext ctx) { return Types.describe("attribute", "attribute", attr.getKey().getKey().replace('_', ' '), "amount", (amount >= 0 ? "+" : "") + amount); }
            };
        }));

        m.registerType(Types.type("glowing", "value", c -> new ActionHandler() {
            @Override
            public boolean apply(ActionContext ctx) {
                Player p = ctx.player();
                if (p == null) return false;
                p.setGlowing(true);
                return true;
            }

            @Override
            public void maintain(ActionContext ctx) {
                Player p = ctx.player();
                if (p != null && !p.isGlowing()) p.setGlowing(true);
            }

            @Override
            public void revert(ActionContext ctx) {
                Player p = ctx.player();
                if (p != null) p.setGlowing(false);
            }

            @Override public boolean revertible() { return true; }
            @Override public String stackKey() { return "glowing"; }
            @Override public String describe(ActionContext ctx) { return Types.describe("glowing"); }
        }));

        m.registerType(Types.type("fire", "ticks", c -> {
            int ticks = (int) c.getLong("ticks", 100);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    p.setFireTicks(Math.max(p.getFireTicks(), ticks));
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p != null && !p.isDead() && p.getFireTicks() < 40) p.setFireTicks(Math.max(40, ticks));
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p != null) p.setFireTicks(0);
                }

                @Override public boolean revertible() { return true; }
                @Override public void repeat(ActionContext ctx) { apply(ctx); }
                @Override public String describe(ActionContext ctx) { return Types.describe("fire"); }
            };
        }));

        m.registerType(Types.type("lightning", "damage", c -> {
            boolean damage = c.getBool("damage", false);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    if (damage) p.getWorld().strikeLightning(p.getLocation());
                    else p.getWorld().strikeLightningEffect(p.getLocation());
                    return true;
                }
            };
        }));

        m.registerType(Types.type("freeze", "ticks", c -> {
            int ticks = (int) c.getLong("ticks", 140);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), Math.max(p.getFreezeTicks(), ticks)));
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p != null && !p.isDead()) p.setFreezeTicks(p.getMaxFreezeTicks());
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p != null) p.setFreezeTicks(0);
                }

                @Override public boolean revertible() { return true; }
                @Override public String describe(ActionContext ctx) { return Types.describe("freeze"); }
            };
        }));

        m.registerType(Types.type("hunger", "food", c -> {
            int food = c.getInt("food", 0);
            float exhaustion = (float) c.getDouble("exhaustion", 0.0);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    if (food != 0) p.setFoodLevel(Math.max(0, Math.min(20, p.getFoodLevel() + food)));
                    if (exhaustion > 0 && !ctx.timed()) p.setExhaustion(p.getExhaustion() + exhaustion);
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p != null && exhaustion > 0 && !p.isDead()) p.setExhaustion(Math.min(40f, p.getExhaustion() + exhaustion));
                }

                @Override public String describe(ActionContext ctx) { return Types.describe("hunger"); }
            };
        }));

        m.registerType(Types.type("gamemode", "mode", c -> {
            GameMode mode;
            try {
                mode = GameMode.valueOf(c.getString("mode", "adventure").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("mode must be survival, creative, adventure or spectator");
            }
            boolean lock = c.getBool("lock", true);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    ctx.data().put("previous", p.getGameMode().name());
                    p.setGameMode(mode);
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p != null && lock && p.getGameMode() != mode) p.setGameMode(mode);
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return;
                    try {
                        p.setGameMode(GameMode.valueOf(String.valueOf(ctx.data().getOrDefault("previous", "SURVIVAL"))));
                    } catch (IllegalArgumentException e) {
                        p.setGameMode(GameMode.SURVIVAL);
                    }
                }

                @Override public boolean revertible() { return true; }
                @Override public String stackKey() { return "gamemode"; }
                @Override public String describe(ActionContext ctx) { return Types.describe("gamemode", "mode", mode.name().toLowerCase(Locale.ROOT)); }
            };
        }));

        m.registerType(Types.type("teleport", "to", c -> {
            String to = c.getString("to", "spawn").trim();
            boolean returnBack = c.getBool("return", true);
            Cfg random = c.getSection("random");
            return new ActionHandler() {
                Location destination(Player p) {
                    String t = to.toLowerCase(Locale.ROOT);
                    if (t.equals("spawn")) {
                        String w = c.getString("world", null);
                        World world = w == null ? Bukkit.getWorlds().getFirst() : Bukkit.getWorld(w);
                        return world == null ? null : world.getSpawnLocation();
                    }
                    if (t.equals("bed") || t.equals("respawn")) {
                        Location bed = p.getRespawnLocation();
                        return bed != null ? bed : Bukkit.getWorlds().getFirst().getSpawnLocation();
                    }
                    if (t.equals("random")) {
                        String w = random.getString("world", c.getString("world", null));
                        World world = w == null ? p.getWorld() : Bukkit.getWorld(w);
                        if (world == null) return null;
                        Location center = random.has("center") ? parseLocation(world.getName() + "," + random.getString("center", "0,0,0")) : world.getSpawnLocation();
                        if (center == null) center = world.getSpawnLocation();
                        return randomSafe(world, center, random.getDouble("min-radius", 100), random.getDouble("radius", 2000));
                    }
                    return parseLocation(to);
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    Location dest = destination(p);
                    if (dest == null) {
                        plugin.getLogger().warning("teleport action: no destination for '" + to + "' (use spawn, bed, random or world,x,y,z).");
                        return false;
                    }
                    ctx.data().put("from", encode(p.getLocation()));
                    p.teleport(dest);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null || !(ctx.data().get("from") instanceof String s)) return;
                    Location back = parseLocation(s);
                    if (back != null) p.teleport(back);
                }

                @Override public boolean revertible() { return returnBack; }
                @Override public String describe(ActionContext ctx) { return Types.describe("teleport", "to", to); }
            };
        }));

        m.registerType(Types.type("heal", "amount", c -> {
            String amount = c.getString("amount", "full");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null || p.isDead()) return false;
                    AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                    double cap = max == null ? 20 : max.getValue();
                    double add = amount.equalsIgnoreCase("full") ? cap : c.getDouble("amount", cap);
                    p.setHealth(Math.max(0.5, Math.min(cap, p.getHealth() + add)));
                    return true;
                }
            };
        }));

        m.registerType(Types.type("feed", "amount", c -> {
            String amount = c.getString("amount", "full");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    int add = amount.equalsIgnoreCase("full") ? 20 : c.getInt("amount", 20);
                    p.setFoodLevel(Math.max(0, Math.min(20, p.getFoodLevel() + add)));
                    if (amount.equalsIgnoreCase("full")) p.setSaturation(Math.max(p.getSaturation(), 5f));
                    return true;
                }
            };
        }));

        m.registerType(Types.type("damage", "amount", c -> {
            double amount = c.getDouble("amount", 2);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null || p.isDead()) return false;
                    p.damage(amount);
                    return true;
                }
            };
        }));

        m.registerType(Types.type("launch", "power", c -> {
            double power = c.getDouble("power", 1.5);
            double x = c.getDouble("x", 0), z = c.getDouble("z", 0);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    p.setVelocity(p.getVelocity().add(new Vector(x, power, z)));
                    return true;
                }
            };
        }));

        m.registerType(Types.type("clear-effects", "value", c -> new ActionHandler() {
            @Override
            public boolean apply(ActionContext ctx) {
                Player p = ctx.player();
                if (p == null) return false;
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                return true;
            }
        }));
    }
}
