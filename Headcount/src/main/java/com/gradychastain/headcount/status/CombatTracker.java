package com.gradychastain.headcount.status;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers when players last dealt or took damage, for the built-in combat statuses. */
public final class CombatTracker implements Listener {
    private final Map<UUID, Long> dealtPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tookPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tookMob = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tookAny = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joined = new ConcurrentHashMap<>();

    /** The player responsible for damage from this entity: the entity itself, a projectile's shooter, TNT's igniter or a pet's owner. */
    public static Player responsiblePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) return p;
        if (damager instanceof Tameable pet && pet.getOwner() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        long now = System.currentTimeMillis();
        Player attacker = responsiblePlayer(event.getDamager());
        if (attacker != null && !attacker.equals(victim)) {
            dealtPlayer.put(attacker.getUniqueId(), now);
            tookPlayer.put(victim.getUniqueId(), now);
        } else if (attacker == null) {
            Entity src = event.getDamager();
            if (src instanceof Projectile proj && proj.getShooter() instanceof Entity e) src = e;
            if (src instanceof Monster || src instanceof org.bukkit.entity.Enemy) tookMob.put(victim.getUniqueId(), now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && event.getFinalDamage() > 0) tookAny.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        joined.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        joined.remove(id);
        // Damage timestamps are kept briefly so a quick relog does not clear a combat status.
    }

    private static boolean within(Map<UUID, Long> map, UUID id, long millis) {
        Long t = map.get(id);
        return t != null && System.currentTimeMillis() - t <= millis;
    }

    public boolean dealtPlayerDamage(UUID id, long millis) { return within(dealtPlayer, id, millis); }
    public boolean tookPlayerDamage(UUID id, long millis) { return within(tookPlayer, id, millis); }
    public boolean tookMobDamage(UUID id, long millis) { return within(tookMob, id, millis); }
    public boolean tookAnyDamage(UUID id, long millis) { return within(tookAny, id, millis); }

    /** Milliseconds since this session's join, or Long.MAX_VALUE if unknown. */
    public long sinceJoin(UUID id) {
        Long t = joined.get(id);
        return t == null ? Long.MAX_VALUE : System.currentTimeMillis() - t;
    }

    public void markJoined(UUID id) {
        joined.putIfAbsent(id, System.currentTimeMillis());
    }

    /** Drops stale damage entries; called periodically. */
    public void prune(long olderThanMillis) {
        long cutoff = System.currentTimeMillis() - olderThanMillis;
        dealtPlayer.values().removeIf(t -> t < cutoff);
        tookPlayer.values().removeIf(t -> t < cutoff);
        tookMob.values().removeIf(t -> t < cutoff);
        tookAny.values().removeIf(t -> t < cutoff);
    }
}
