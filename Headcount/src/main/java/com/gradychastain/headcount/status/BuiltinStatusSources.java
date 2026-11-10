package com.gradychastain.headcount.status;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.ConfigView;
import com.gradychastain.headcount.api.status.StatusCheck;
import com.gradychastain.headcount.api.status.StatusResolver;
import com.gradychastain.headcount.api.status.StatusSourceType;
import com.gradychastain.headcount.integration.moderation.ModerationHook;
import com.gradychastain.headcount.integration.pvp.PvPHook;
import com.gradychastain.headcount.util.NumberRange;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Registers every built-in status source type. See docs/statuses.md for their settings. */
final class BuiltinStatusSources {
    private BuiltinStatusSources() {}

    private static StatusSourceType type(String id, BiFunction<ConfigView, StatusResolver, StatusCheck> factory) {
        return new StatusSourceType() {
            @Override public String id() { return id; }
            @Override public StatusCheck create(ConfigView settings, StatusResolver statuses) { return factory.apply(settings, statuses); }
        };
    }

    private static Set<String> lowerSet(List<String> list) {
        return list.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static long seconds(ConfigView c, String key, long def) {
        return c.getMillis(key, def * 1000L);
    }

    static void registerAll(StatusManager m, HeadcountPlugin plugin) {
        // --- PvP plugin (direct hook) ---
        m.registerType(type("pvp", (c, s) -> {
            String check = c.getString("check", "combat-tagged").toLowerCase(Locale.ROOT);
            return p -> {
                PvPHook hook = plugin.integrations().pvp();
                if (hook == null) return null;
                return switch (check) {
                    case "pvp-enabled", "pvp-on" -> hook.pvpEnabled(p);
                    case "pvp-disabled", "pvp-off" -> {
                        Boolean on = hook.pvpEnabled(p);
                        yield on == null ? null : !on;
                    }
                    case "combat-tagged", "tagged", "in-combat" -> hook.combatTagged(p);
                    case "newbie", "newbie-protected" -> hook.newbie(p);
                    case "respawn-protected" -> hook.respawnProtected(p);
                    case "override" -> hook.override(p);
                    default -> throw new IllegalArgumentException("unknown pvp check '" + check + "'");
                };
            };
        }));

        // --- PlaceholderAPI (or Headcount) placeholder compared to a value ---
        m.registerType(type("placeholder", (c, s) -> {
            String placeholder = c.getString("placeholder", "");
            if (placeholder.isBlank()) throw new IllegalArgumentException("placeholder is required");
            String op = c.getString("operator", "equals").toLowerCase(Locale.ROOT);
            String value = c.getString("value", "true");
            boolean ignoreCase = c.getBool("ignore-case", true);
            Pattern regex = op.equals("regex") || op.equals("matches") ? Pattern.compile(value, ignoreCase ? Pattern.CASE_INSENSITIVE : 0) : null;
            compare("0", op, value, ignoreCase, regex); // throws on an unknown operator so the status is reported at load
            return p -> {
                String raw = plugin.text().resolveForNumber(placeholder, p);
                if (raw.equals(placeholder)) return null; // not resolved: PlaceholderAPI or the expansion is missing
                return compare(raw, op, value, ignoreCase, regex);
            };
        }));

        m.registerType(type("scoreboard-tag", (c, s) -> {
            String tag = c.getString("tag", "");
            return p -> p.getScoreboardTags().contains(tag);
        }));

        m.registerType(type("permission", (c, s) -> {
            String node = c.getString("permission", "");
            return p -> p.hasPermission(node);
        }));

        m.registerType(type("metadata", (c, s) -> metadataCheck(c.getString("key", ""), c.getString("value", null))));

        m.registerType(type("flag", (c, s) -> {
            String flag = c.getString("flag", "").toLowerCase(Locale.ROOT);
            return p -> plugin.players().hasFlag(p.getUniqueId(), flag);
        }));

        // --- Combat ---
        m.registerType(type("pvp-damage", (c, s) -> {
            String dir = c.getString("direction", "either").toLowerCase(Locale.ROOT);
            long ms = seconds(c, "seconds", 15);
            return p -> switch (dir) {
                case "dealt" -> plugin.combat().dealtPlayerDamage(p.getUniqueId(), ms);
                case "took", "taken" -> plugin.combat().tookPlayerDamage(p.getUniqueId(), ms);
                default -> plugin.combat().dealtPlayerDamage(p.getUniqueId(), ms) || plugin.combat().tookPlayerDamage(p.getUniqueId(), ms);
            };
        }));
        m.registerType(type("recent-damage", (c, s) -> {
            long ms = seconds(c, "seconds", 10);
            return p -> plugin.combat().tookAnyDamage(p.getUniqueId(), ms);
        }));
        m.registerType(type("mob-damage", (c, s) -> {
            long ms = seconds(c, "seconds", 10);
            return p -> plugin.combat().tookMobDamage(p.getUniqueId(), ms);
        }));
        m.registerType(type("nearby-player", (c, s) -> {
            double r = c.getDouble("radius", 16);
            String status = c.getString("status", "");
            int min = Math.max(1, c.getInt("min", 1));
            boolean visibleOnly = c.getBool("visible-only", true);
            return p -> {
                int found = 0;
                for (Entity e : p.getNearbyEntities(r, r, r)) {
                    if (!(e instanceof Player other) || other.equals(p)) continue;
                    if (other.getLocation().distanceSquared(p.getLocation()) > r * r) continue;
                    if (visibleOnly && !p.canSee(other)) continue;
                    if (!status.isBlank() && !s.has(other, status)) continue;
                    if (++found >= min) return true;
                }
                return false;
            };
        }));
        m.registerType(type("nearby-hostile", (c, s) -> {
            double r = c.getDouble("radius", 8);
            boolean targeting = c.getBool("include-targeting", true);
            double tr = c.getDouble("targeting-radius", 32);
            return p -> {
                double max = Math.max(r, targeting ? tr : 0);
                for (Entity e : p.getNearbyEntities(max, max, max)) {
                    if (!(e instanceof Enemy) || e.isDead()) continue;   // a mob in its death animation is gone
                    double d2 = e.getLocation().distanceSquared(p.getLocation());
                    if (d2 <= r * r) return true;
                    if (targeting && e instanceof Mob mob && p.equals(mob.getTarget()) && d2 <= tr * tr) return true;
                }
                return false;
            };
        }));
        m.registerType(type("targeted-by-mob", (c, s) -> {
            double r = c.getDouble("radius", 32);
            return p -> {
                for (Entity e : p.getNearbyEntities(r, r, r)) {
                    if (e instanceof Mob mob && !mob.isDead() && p.equals(mob.getTarget())) return true;
                }
                return false;
            };
        }));

        // --- Player state ---
        m.registerType(type("health", (c, s) -> {
            NumberRange range = c.has("below") ? new NumberRange(Double.NEGATIVE_INFINITY, c.getDouble("below", 6), false, true, null)
                    : NumberRange.parse(c.getString("range", "<6"));
            boolean percent = c.getBool("percent", false);
            return p -> {
                double hp = p.getHealth();
                if (percent) {
                    AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                    hp = max == null || max.getValue() <= 0 ? 0 : hp / max.getValue() * 100;
                }
                return range.test(hp);
            };
        }));
        m.registerType(type("food", (c, s) -> {
            NumberRange range = c.has("below") ? new NumberRange(Double.NEGATIVE_INFINITY, c.getDouble("below", 6), false, true, null)
                    : NumberRange.parse(c.getString("range", "<6"));
            return p -> range.test(p.getFoodLevel());
        }));
        m.registerType(type("on-fire", (c, s) -> p -> p.getFireTicks() > 0 || p.getVisualFire() == net.kyori.adventure.util.TriState.TRUE));
        m.registerType(type("in-lava", (c, s) -> p -> p.isInLava()));
        m.registerType(type("in-water", (c, s) -> p -> p.isInWater()));
        m.registerType(type("falling", (c, s) -> {
            double d = c.getDouble("distance", 4);
            return p -> !p.isFlying() && !p.isGliding() && p.getFallDistance() > d;
        }));
        m.registerType(type("gliding", (c, s) -> p -> p.isGliding()));
        m.registerType(type("flying", (c, s) -> p -> p.isFlying()));
        m.registerType(type("riding", (c, s) -> {
            Set<String> kinds = lowerSet(c.getStringList("vehicles"));
            return p -> {
                Entity v = p.getVehicle();
                if (v == null) return false;
                return kinds.isEmpty() || kinds.contains(v.getType().getKey().getKey()) || kinds.contains(v.getType().name().toLowerCase(Locale.ROOT));
            };
        }));
        m.registerType(type("swimming", (c, s) -> p -> p.isSwimming()));
        m.registerType(type("sneaking", (c, s) -> p -> p.isSneaking()));
        m.registerType(type("sprinting", (c, s) -> p -> p.isSprinting()));
        m.registerType(type("sleeping", (c, s) -> p -> p.isSleeping()));
        m.registerType(type("dead", (c, s) -> p -> p.isDead() || p.getHealth() <= 0));
        m.registerType(type("gamemode", (c, s) -> {
            Set<GameMode> modes = new HashSet<>();
            for (String g : c.getStringList("modes")) {
                try {
                    modes.add(GameMode.valueOf(g.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("unknown gamemode '" + g + "'");
                }
            }
            return p -> modes.contains(p.getGameMode());
        }));
        m.registerType(type("world", (c, s) -> {
            Set<String> worlds = lowerSet(c.getStringList("worlds"));
            return p -> worlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
        }));
        m.registerType(type("joined-recently", (c, s) -> {
            long ms = seconds(c, "seconds", 30);
            return p -> plugin.combat().sinceJoin(p.getUniqueId()) <= ms;
        }));
        m.registerType(type("invisible", (c, s) -> p -> p.isInvisible() || p.hasPotionEffect(PotionEffectType.INVISIBILITY)));
        m.registerType(type("potion", (c, s) -> {
            String effect = c.getString("effect", "");
            PotionEffectType t = Registry.MOB_EFFECT.get(key(effect));
            if (t == null) throw new IllegalArgumentException("unknown potion effect '" + effect + "'");
            int minAmp = c.getInt("min-amplifier", 0);
            return p -> {
                PotionEffect pe = p.getPotionEffect(t);
                return pe != null && pe.getAmplifier() >= minAmp;
            };
        }));
        m.registerType(type("y-level", (c, s) -> {
            NumberRange range = NumberRange.parse(c.getString("range", c.has("below") ? "<" + c.getString("below", "0")
                    : c.has("above") ? ">" + c.getString("above", "0") : "any"));
            return p -> range.test(p.getLocation().getY());
        }));
        m.registerType(type("biome", (c, s) -> {
            Set<String> biomes = lowerSet(c.getStringList("biomes"));
            return p -> {
                Biome b = p.getLocation().getBlock().getBiome();
                return biomes.contains(b.getKey().getKey()) || biomes.contains(b.getKey().toString());
            };
        }));
        m.registerType(type("holding", (c, s) -> {
            Set<Material> mats = new HashSet<>();
            for (String name : c.getStringList("materials")) {
                Material mat = Material.matchMaterial(name);
                if (mat == null) throw new IllegalArgumentException("unknown material '" + name + "'");
                mats.add(mat);
            }
            String hand = c.getString("hand", "any").toLowerCase(Locale.ROOT);
            return p -> {
                boolean main = mats.contains(p.getInventory().getItemInMainHand().getType());
                boolean off = mats.contains(p.getInventory().getItemInOffHand().getType());
                return switch (hand) {
                    case "main", "main-hand" -> main;
                    case "off", "off-hand" -> off;
                    default -> main || off;
                };
            };
        }));
        m.registerType(type("world-time", (c, s) -> {
            long from = c.getLong("from", 13000), to = c.getLong("to", 23000);
            return p -> {
                long t = p.getWorld().getTime();
                return from <= to ? t >= from && t <= to : t >= from || t <= to;
            };
        }));
        m.registerType(type("op", (c, s) -> p -> p.isOp()));
        m.registerType(type("bedrock", (c, s) -> p -> plugin.integrations().isBedrock(p.getUniqueId())));
        m.registerType(type("essentials", (c, s) -> {
            String check = c.getString("check", "afk");
            return p -> plugin.integrations().essentials() == null ? null : plugin.integrations().essentials().check(p, check);
        }));
        m.registerType(type("muted", (c, s) -> p -> {
            ModerationHook hook = plugin.integrations().moderation(ModerationHook.Kind.MUTE);
            if (hook != null && !hook.enforcedByHeadcount(ModerationHook.Kind.MUTE)) {
                Boolean v = hook.isMuted(p.getUniqueId());
                if (v != null) return v;
            }
            return plugin.actions().hasActiveType(p.getUniqueId(), "mute");
        }));
        m.registerType(type("in-trade", (c, s) -> p -> plugin.trades().isBusy(p.getUniqueId())));
        m.registerType(type("headcount", (c, s) -> {
            String value = c.getString("value", "points").toLowerCase(Locale.ROOT);
            NumberRange range = NumberRange.parse(c.getString("range", ">0"));
            return p -> {
                Double v = plugin.scores().variable(p, value);
                return v == null ? null : range.test(v);
            };
        }));

        // --- Combinations ---
        m.registerType(type("all-of", (c, s) -> {
            List<String> list = c.getStringList("statuses");
            if (list.isEmpty()) throw new IllegalArgumentException("all-of needs a 'statuses' list");
            return p -> {
                for (String n : list) if (!s.has(p, n)) return false;
                return true;
            };
        }));
        m.registerType(type("any-of", (c, s) -> {
            List<String> list = c.getStringList("statuses");
            if (list.isEmpty()) throw new IllegalArgumentException("any-of needs a 'statuses' list");
            return p -> {
                for (String n : list) if (s.has(p, n)) return true;
                return false;
            };
        }));
        m.registerType(type("not", (c, s) -> {
            String n = c.getString("status", "");
            if (n.isBlank()) throw new IllegalArgumentException("not needs a 'status'");
            return p -> !s.has(p, n);
        }));
        m.registerType(type("always", (c, s) -> p -> true));
        m.registerType(type("never", (c, s) -> p -> false));
    }

    /**
     * Bukkit metadata is deprecated by Paper, but vanish plugins still set it (for example "vanished"), so this
     * source keeps reading it on purpose.
     */
    @SuppressWarnings("deprecation")
    private static StatusCheck metadataCheck(String key, String expect) {
        return p -> {
            for (MetadataValue v : p.getMetadata(key)) {
                if (expect == null ? v.asBoolean() : expect.equalsIgnoreCase(v.asString())) return true;
            }
            return false;
        };
    }

    static NamespacedKey key(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = k.contains(":") ? NamespacedKey.fromString(k) : NamespacedKey.minecraft(k);
        if (key == null) throw new IllegalArgumentException("invalid key '" + s + "'");
        return key;
    }

    static boolean compare(String actual, String op, String expected, boolean ignoreCase, Pattern regex) {
        String a = actual.trim();
        String b = expected.trim();
        Double na = num(a), nb = num(b);
        return switch (op) {
            case "equals", "=", "==", "is" -> na != null && nb != null ? na.doubleValue() == nb.doubleValue() : ignoreCase ? a.equalsIgnoreCase(b) : a.equals(b);
            case "not-equals", "!=", "is-not" -> !(na != null && nb != null ? na.doubleValue() == nb.doubleValue() : ignoreCase ? a.equalsIgnoreCase(b) : a.equals(b));
            case "greater-than", ">" -> na != null && nb != null && na > nb;
            case "less-than", "<" -> na != null && nb != null && na < nb;
            case "greater-or-equal", ">=" -> na != null && nb != null && na >= nb;
            case "less-or-equal", "<=" -> na != null && nb != null && na <= nb;
            case "contains" -> ignoreCase ? a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT)) : a.contains(b);
            case "not-contains" -> !(ignoreCase ? a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT)) : a.contains(b));
            case "starts-with" -> ignoreCase ? a.toLowerCase(Locale.ROOT).startsWith(b.toLowerCase(Locale.ROOT)) : a.startsWith(b);
            case "ends-with" -> ignoreCase ? a.toLowerCase(Locale.ROOT).endsWith(b.toLowerCase(Locale.ROOT)) : a.endsWith(b);
            case "regex", "matches" -> regex != null && regex.matcher(a).find();
            default -> throw new IllegalArgumentException("unknown operator '" + op + "'");
        };
    }

    private static Double num(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
