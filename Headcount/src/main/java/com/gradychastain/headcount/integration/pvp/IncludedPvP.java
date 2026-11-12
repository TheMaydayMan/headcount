package com.gradychastain.headcount.integration.pvp;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.status.CombatTracker;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Headcount's own PvP system, used when integrations.yml {@code pvp.provider} is {@code included}: a per-player PvP
 * toggle (/pvp) with a default, a cooldown and forced worlds; newbie and respawn protection; a combat tag with
 * restrictions (commands, teleports, flight, gliding, ender pearls, turning PvP off); and combat-log punishment. Every
 * message's channel and sound is set in integrations.yml, and its text in lang/en_US.yml under {@code pvp}.
 */
public final class IncludedPvP implements PvPHook, Listener, TabExecutor {
    private final HeadcountPlugin plugin;
    private boolean active;
    private final Map<UUID, Long> tagUntil = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, Long> respawnUntil = new HashMap<>();
    private final Map<UUID, Long> lastToggle = new HashMap<>();
    private final Map<UUID, Boolean> locks = new HashMap<>();
    private final Map<String, Long> lastNotice = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<UUID> kicked = new HashSet<>();
    private BukkitTask task;

    public IncludedPvP(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private Cfg cfg() {
        return plugin.configs().integrations().getSection("pvp.included");
    }

    /** Turns the system on or off (integrations.yml pvp.provider). Listeners are registered once and idle while off. */
    public void setActive(boolean on) {
        active = on;
        if (task != null) task.cancel();
        task = null;
        if (on) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        } else {
            for (UUID id : new ArrayList<>(bars.keySet())) hideBar(id);
            tagUntil.clear();
        }
    }

    public boolean isActive() {
        return active;
    }

    // ---------------------------------------------------------------- PvPHook

    @Override public String name() { return "included"; }

    @Override
    public Boolean pvpEnabled(Player p) {
        if (!active) return null;
        String w = p.getWorld().getName();
        if (cfg().getStringList("toggle.force-on-worlds").contains(w)) return true;
        if (cfg().getStringList("toggle.force-off-worlds").contains(w)) return false;
        if (Boolean.TRUE.equals(newbie(p))) return false;
        Boolean lock = locks.get(p.getUniqueId());
        if (lock != null) return lock;
        PlayerData d = plugin.players().get(p.getUniqueId());
        if (d != null && d.pvp != null) return d.pvp;
        return defaultState();
    }

    private boolean defaultState() {
        return cfg().getString("toggle.default", "off").equalsIgnoreCase("on");
    }

    @Override
    public Boolean combatTagged(Player p) {
        if (!active) return null;
        return tagLeft(p.getUniqueId()) > 0;
    }

    @Override
    public Boolean newbie(Player p) {
        if (!active) return null;
        long dur = cfg().getMillis("newbie-protection.duration", 600_000);
        if (dur <= 0) return false;
        PlayerData d = plugin.players().get(p.getUniqueId());
        // Toggling PvP on yourself ends newbie protection early (d.pvp is then set).
        if (d == null || d.pvp != null) return false;
        return System.currentTimeMillis() - d.firstJoin < dur;
    }

    @Override
    public Boolean respawnProtected(Player p) {
        if (!active) return null;
        Long until = respawnUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    @Override
    public Boolean override(Player p) {
        if (!active) return null;
        return p.hasPermission("headcount.pvp.override");
    }

    @Override
    public Long tagSecondsLeft(Player p) {
        if (!active) return null;
        return (tagLeft(p.getUniqueId()) + 999) / 1000;
    }

    @Override
    public boolean setPvP(Player p, boolean enabled) {
        if (!active) return false;
        store(p, enabled);
        return true;
    }

    @Override
    public void lock(Player p, Boolean state) {
        if (state == null) locks.remove(p.getUniqueId());
        else locks.put(p.getUniqueId(), state);
    }

    private void store(Player p, boolean enabled) {
        PlayerData d = plugin.players().get(p.getUniqueId());
        if (d != null) d.pvp = enabled;
        plugin.playerStore().set(p.getUniqueId(), "pvp", enabled);
        plugin.statuses().invalidate(p.getUniqueId());
    }

    // ---------------------------------------------------------------- rules

    private long tagLeft(UUID id) {
        Long until = tagUntil.get(id);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    /** Why these two players can't fight, as a lang key under pvp.blocked-hit, or null when they can. */
    private String blockReason(Player attacker, Player victim) {
        if (attacker.hasPermission("headcount.pvp.override")) return null;
        if (Boolean.TRUE.equals(newbie(attacker))) return "self-newbie";
        if (Boolean.TRUE.equals(newbie(victim))) return "target-newbie";
        if (Boolean.TRUE.equals(respawnProtected(victim))) return "target-respawn";
        if (Boolean.TRUE.equals(respawnProtected(attacker))) respawnUntil.remove(attacker.getUniqueId()); // attacking ends it
        if (!Boolean.TRUE.equals(pvpEnabled(attacker))) return "self-off";
        if (!Boolean.TRUE.equals(pvpEnabled(victim))) return "target-off";
        return null;
    }

    private void tag(Player p, Player other) {
        if (!cfg().getBool("combat-tag.enabled", true)) return;
        boolean was = tagLeft(p.getUniqueId()) > 0;
        tagUntil.put(p.getUniqueId(), System.currentTimeMillis() + cfg().getMillis("combat-tag.duration", 15_000));
        plugin.statuses().invalidate(p.getUniqueId());
        if (!was) {
            notify(p, "tag-start", Vars.of("other", other.getName(), "seconds", tagSecondsLeft(p)));
            if (cfg().getBool("combat-tag.block-flight", true) && p.isFlying() && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                p.setFlying(false);
            }
        }
    }

    private void untag(UUID id, boolean announce) {
        if (tagUntil.remove(id) == null) return;
        lastAttacker.remove(id);
        hideBar(id);
        plugin.statuses().invalidate(id);
        Player p = Bukkit.getPlayer(id);
        if (announce && p != null) notify(p, "tag-end", Vars.empty());
    }

    private boolean tagged(Player p) {
        return active && tagLeft(p.getUniqueId()) > 0;
    }

    private void tick() {
        if (!active) return;
        String display = cfg().getString("messages.countdown", "actionbar").toLowerCase(Locale.ROOT);
        for (UUID id : new ArrayList<>(tagUntil.keySet())) {
            long left = tagLeft(id);
            if (left <= 0) {
                untag(id, true);
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Vars v = Vars.of("seconds", (left + 999) / 1000);
            switch (display) {
                case "actionbar" -> plugin.lang().actionBar(p, "pvp.countdown", v);
                case "bossbar" -> {
                    float progress = (float) Math.min(1, left / (double) Math.max(1, cfg().getMillis("combat-tag.duration", 15_000)));
                    BossBar bar = bars.computeIfAbsent(id, k -> {
                        BossBar.Color color;
                        try {
                            color = BossBar.Color.valueOf(cfg().getString("messages.countdown-bossbar-color", "red").toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException e) {
                            color = BossBar.Color.RED;
                        }
                        BossBar b = BossBar.bossBar(plugin.lang().get("pvp.countdown", v), progress, color, BossBar.Overlay.PROGRESS);
                        p.showBossBar(b);
                        return b;
                    });
                    bar.name(plugin.lang().get("pvp.countdown", v));
                    bar.progress(progress);
                }
                default -> { }
            }
        }
        respawnUntil.values().removeIf(t -> t < System.currentTimeMillis());
    }

    private void hideBar(UUID id) {
        BossBar bar = bars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (bar != null && p != null) p.hideBossBar(bar);
    }

    /**
     * A blocked-hit style message (channel and sound from messages.blocked-hit), sent whichever PvP provider is in use.
     * Pet protection uses it with PvPManager too.
     */
    public void notifyBlocked(Player p, String langKey, Vars vars) {
        notify(p, "blocked-hit", langKey, vars);
    }

    /**
     * Sends a pvp.&lt;event&gt; message through the channel set in integrations.yml (pvp.included.messages.&lt;event&gt;),
     * with its sound. Repeats of the same message to the same player are held back for a second.
     */
    private void notify(Player p, String event, Vars vars) {
        notify(p, event, "pvp." + event, vars);
    }

    private void notify(Player p, String event, String langKey, Vars vars) {
        String key = p.getUniqueId() + ":" + langKey;
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(key);
        if (last != null && now - last < 1000) return;
        lastNotice.put(key, now);
        Cfg m = cfg().getSection("messages." + event);
        String channel = m.getString("channel", "chat").toLowerCase(Locale.ROOT);
        switch (channel) {
            case "actionbar" -> plugin.lang().actionBar(p, langKey, vars);
            case "title" -> p.showTitle(Title.title(net.kyori.adventure.text.Component.empty(), plugin.lang().get(langKey, vars)));
            case "none" -> { }
            default -> plugin.lang().send(p, langKey, vars);
        }
        String sound = m.getString("sound", "");
        if (!sound.isBlank()) {
            try {
                p.playSound(Sound.sound(Key.key(sound), Sound.Source.PLAYER, (float) m.getDouble("volume", 1), (float) m.getDouble("pitch", 1)));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("integrations.yml pvp.included.messages." + event + ".sound: '" + sound + "' is not a sound key.");
            }
        }
    }

    // ---------------------------------------------------------------- damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!active || !(event.getEntity() instanceof Player victim)) return;
        Player attacker = CombatTracker.responsiblePlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        String reason = blockReason(attacker, victim);
        if (reason != null) {
            event.setCancelled(true);
            notify(attacker, "blocked-hit", "pvp.blocked-hit." + reason, Vars.of("target", victim.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDone(EntityDamageByEntityEvent event) {
        if (!active || !(event.getEntity() instanceof Player victim)) return;
        Player attacker = CombatTracker.responsiblePlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        if (cfg().getBool("combat-tag.tag-victim", true)) tag(victim, attacker);
        if (cfg().getBool("combat-tag.tag-attacker", true)) tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (!active || !(event.getEntity() instanceof Player victim)) return;
        Player attacker = CombatTracker.responsiblePlayer(event.getCombuster());
        if (attacker != null && !attacker.equals(victim) && blockReason(attacker, victim) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!active || !(event.getPotion().getShooter() instanceof Player thrower)) return;
        boolean harmful = false;
        for (PotionEffect e : event.getPotion().getEffects()) {
            if (e.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) harmful = true;
        }
        if (!harmful) return;
        for (LivingEntity e : event.getAffectedEntities()) {
            if (e instanceof Player victim && !victim.equals(thrower) && blockReason(thrower, victim) != null) event.setIntensity(victim, 0);
        }
    }

    // ---------------------------------------------------------------- restrictions while tagged

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!tagged(p) || p.hasPermission("headcount.bypass.combat-commands")) return;
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        String bare = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        for (String blocked : cfg().getStringList("combat-tag.blocked-commands")) {
            if (bare.equalsIgnoreCase(blocked.startsWith("/") ? blocked.substring(1) : blocked)) {
                event.setCancelled(true);
                notify(p, "blocked-action", "pvp.blocked-command", Vars.of("command", "/" + bare, "seconds", tagSecondsLeft(p)));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (!tagged(p) || !cfg().getBool("combat-tag.block-teleport", true) || p.hasPermission("headcount.bypass.combat-commands")) return;
        var cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
            notify(p, "blocked-action", "pvp.blocked-teleport", Vars.of("seconds", tagSecondsLeft(p)));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (event.isFlying() && tagged(p) && cfg().getBool("combat-tag.block-flight", true) && p.getGameMode() != GameMode.CREATIVE
                && p.getGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            notify(p, "blocked-action", "pvp.blocked-flight", Vars.of("seconds", tagSecondsLeft(p)));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player p && tagged(p) && cfg().getBool("combat-tag.block-elytra", false)) {
            event.setCancelled(true);
            notify(p, "blocked-action", "pvp.blocked-elytra", Vars.of("seconds", tagSecondsLeft(p)));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPearl(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL) return;
        if (event.getAction().isRightClick() && tagged(p) && cfg().getBool("combat-tag.block-ender-pearls", false)) {
            event.setCancelled(true);
            notify(p, "blocked-action", "pvp.blocked-pearl", Vars.of("seconds", tagSecondsLeft(p)));
        }
    }

    // ---------------------------------------------------------------- join, death, respawn, quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!active || !cfg().getBool("toggle.reset-on-join", false)) return;
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerData d = plugin.players().get(p.getUniqueId());
            if (d != null && d.pvp != null) store(p, defaultState());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (active && cfg().getBool("combat-tag.untag-on-death", true)) untag(event.getEntity().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!active) return;
        long ms = cfg().getMillis("respawn-protection.duration", 5_000);
        if (ms > 0) respawnUntil.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + ms);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKick(PlayerKickEvent event) {
        kicked.add(event.getPlayer().getUniqueId());
    }

    /** Runs before other quit handlers, while the player can still die and drop items. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        boolean wasKicked = kicked.remove(id);
        hideBar(id);
        if (!tagged(p)) return;
        Cfg log = cfg().getSection("combat-log");
        UUID attackerId = lastAttacker.get(id);
        untag(id, false);
        if (!log.getBool("enabled", true) || wasKicked || p.hasPermission("headcount.bypass.combat-log") || p.isDead()) return;
        if (log.getString("punishment", "kill").equalsIgnoreCase("kill")) {
            Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
            if (attacker != null && log.getBool("credit-last-attacker", true)) p.setKiller(attacker);
            p.setHealth(0);
        }
        Object actions = log.get("actions");
        if (actions != null) {
            var list = plugin.actions().parseList(actions, "integrations.yml:pvp.included.combat-log.actions");
            if (!list.isEmpty()) plugin.actions().run(list, id, Vars.of("player", p.getName()), "combat-log");
        }
        if (log.getBool("broadcast", true)) {
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) plugin.lang().send(o, "pvp.combat-log-broadcast", "player", p.getName());
        }
    }

    // ---------------------------------------------------------------- /pvp

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (!active) {
            plugin.lang().send(s, "pvp.not-included");
            return true;
        }
        // /pvp <player> [on|off] (admins)
        if (a.length >= 1 && !List.of("on", "off", "toggle", "status").contains(a[0].toLowerCase(Locale.ROOT))) {
            if (!s.hasPermission("headcount.pvp.others")) {
                plugin.lang().send(s, "general.no-permission");
                return true;
            }
            Player t = Bukkit.getPlayerExact(a[0]);
            if (t == null) {
                plugin.lang().send(s, "admin.player-offline", "player", a[0]);
                return true;
            }
            if (a.length >= 2 && (a[1].equalsIgnoreCase("on") || a[1].equalsIgnoreCase("off"))) {
                store(t, a[1].equalsIgnoreCase("on"));
                notify(t, "toggled", Boolean.TRUE.equals(pvpEnabled(t)) ? "pvp.toggled-on" : "pvp.toggled-off", Vars.empty());
            }
            plugin.lang().send(s, "pvp.other-status", "player", t.getName(), "state", state(pvpEnabled(t)),
                    "tagged", tagged(t) ? plugin.lang().plain("pvp.word-yes") : plugin.lang().plain("pvp.word-no"));
            return true;
        }
        if (!(s instanceof Player p)) {
            plugin.lang().send(s, "general.players-only");
            return true;
        }
        String op = a.length == 0 ? "toggle" : a[0].toLowerCase(Locale.ROOT);
        boolean current = Boolean.TRUE.equals(pvpEnabled(p));
        if (op.equals("status")) {
            plugin.lang().send(p, "pvp.status", "state", state(current), "seconds", tagSecondsLeft(p));
            return true;
        }
        boolean want = op.equals("toggle") ? !current : op.equals("on");
        String world = p.getWorld().getName();
        if (cfg().getStringList("toggle.force-on-worlds").contains(world) || cfg().getStringList("toggle.force-off-worlds").contains(world)) {
            notify(p, "blocked-action", "pvp.forced-world", Vars.of("state", state(current)));
            return true;
        }
        PlayerData d = plugin.players().get(p);
        boolean isNewbie = Boolean.TRUE.equals(newbie(p));
        if (isNewbie && want) {
            if (!cfg().getBool("newbie-protection.can-end-early", true)) {
                notify(p, "blocked-action", "pvp.newbie-locked", Vars.empty());
                return true;
            }
        } else if (want == current) {
            plugin.lang().send(p, "pvp.status", "state", state(current), "seconds", tagSecondsLeft(p));
            return true;
        }
        if (!cfg().getBool("toggle.allow-toggle", true) || !p.hasPermission("headcount.pvp")) {
            notify(p, "blocked-action", "pvp.toggle-disabled", Vars.empty());
            return true;
        }
        Boolean lock = locks.get(p.getUniqueId());
        if (lock != null && lock != want) {
            plugin.lang().send(p, "pvp.locked", "state", state(lock));
            return true;
        }
        if (!want && tagged(p) && cfg().getBool("combat-tag.block-pvp-toggle", true)) {
            notify(p, "blocked-action", "pvp.blocked-toggle", Vars.of("seconds", tagSecondsLeft(p)));
            return true;
        }
        long cooldown = cfg().getMillis("toggle.cooldown", 10_000);
        Long last = lastToggle.get(p.getUniqueId());
        if (last != null && cooldown > 0 && !p.hasPermission("headcount.bypass.pvp.cooldown")) {
            long left = cooldown - (System.currentTimeMillis() - last);
            if (left > 0) {
                notify(p, "blocked-action", "pvp.cooldown", Vars.of("seconds", (left + 999) / 1000));
                return true;
            }
        }
        lastToggle.put(p.getUniqueId(), System.currentTimeMillis());
        store(p, want);
        if (isNewbie && d != null) notify(p, "toggled", "pvp.newbie-ended", Vars.empty());
        notify(p, "toggled", want ? "pvp.toggled-on" : "pvp.toggled-off", Vars.empty());
        return true;
    }

    private String state(Boolean on) {
        return plugin.lang().plain(Boolean.TRUE.equals(on) ? "pvp.word-on" : "pvp.word-off");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        if (a.length == 1) {
            List<String> out = new ArrayList<>(List.of("on", "off", "status"));
            if (s.hasPermission("headcount.pvp.others")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && s.hasPermission("headcount.pvp.others")) return List.of("on", "off");
        return List.of();
    }
}
