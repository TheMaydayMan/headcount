package com.gradychastain.headcount.integration.pvp;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.status.CombatTracker;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.UUID;

/**
 * integrations.yml {@code pvp.pet-protection}: a player can't hurt another player's tamed animal unless the two of them
 * could fight. Uses whichever PvP provider is chosen (included or PvPManager); with {@code none} there are no PvP
 * rules, so it does nothing. A pet's owner can always hurt their own pet.
 */
public final class PetProtection implements Listener {
    private final HeadcountPlugin plugin;

    public PetProtection(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private Cfg cfg() {
        return plugin.configs().integrations().getSection("pvp.pet-protection");
    }

    /** Why this player can't hurt this pet, as a lang key, or null when they can. */
    private String blockReason(Player attacker, UUID ownerId) {
        if (!plugin.isLoaded() || !cfg().getBool("enabled", false)) return null;
        PvPHook hook = plugin.integrations().pvp();
        if (hook == null || attacker.getUniqueId().equals(ownerId)) return null;
        if (Boolean.TRUE.equals(hook.override(attacker))) return null;
        if (Boolean.TRUE.equals(hook.newbie(attacker))) return "pvp.blocked-hit.self-newbie";
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) {
            return cfg().getString("offline-owners", "protect").toLowerCase(Locale.ROOT).equals("allow") ? null : "pvp.blocked-pet.owner-offline";
        }
        if (Boolean.TRUE.equals(hook.newbie(owner))) return "pvp.blocked-pet.target-newbie";
        if (Boolean.TRUE.equals(hook.respawnProtected(owner))) return "pvp.blocked-pet.target-respawn";
        if (Boolean.FALSE.equals(hook.pvpEnabled(attacker))) return "pvp.blocked-hit.self-off";
        if (Boolean.FALSE.equals(hook.pvpEnabled(owner))) return "pvp.blocked-pet.target-off";
        return null;
    }

    private static UUID owner(Entity e) {
        return e instanceof Tameable t && t.isTamed() ? t.getOwnerUniqueId() : null;
    }

    private void tell(Player attacker, String key, Entity pet, UUID ownerId) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(ownerId);
        Component name = pet.customName() != null ? pet.customName() : Component.translatable(pet.getType().translationKey());
        Vars v = Vars.of("owner", o.getName() == null ? ownerId.toString() : o.getName(), "pet", name);
        plugin.integrations().includedPvP().notifyBlocked(attacker, key, v);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID ownerId = owner(event.getEntity());
        if (ownerId == null) return;
        Player attacker = CombatTracker.responsiblePlayer(event.getDamager());
        if (attacker == null) return;
        String reason = blockReason(attacker, ownerId);
        if (reason == null) return;
        event.setCancelled(true);
        tell(attacker, reason, event.getEntity(), ownerId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        UUID ownerId = owner(event.getEntity());
        if (ownerId == null) return;
        Player attacker = CombatTracker.responsiblePlayer(event.getCombuster());
        if (attacker != null && blockReason(attacker, ownerId) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) return;
        boolean harmful = false;
        for (PotionEffect e : event.getPotion().getEffects()) {
            if (e.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) harmful = true;
        }
        if (!harmful) return;
        for (LivingEntity e : event.getAffectedEntities()) {
            UUID ownerId = owner(e);
            if (ownerId != null && blockReason(thrower, ownerId) != null) event.setIntensity(e, 0);
        }
    }
}
