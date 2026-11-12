package com.gradychastain.headcount.hook;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.status.CombatTracker;
import com.gradychastain.headcount.text.Vars;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Player hooks that follow something a player did to someone else (config.yml {@code hooks.player}):
 * <ul>
 *   <li>{@code hurt-pet}: hurt another player's tamed animal (directly, or with a projectile, TNT, a potion cloud or
 *   their own pet);</li>
 *   <li>{@code kill-pet}: killed one;</li>
 *   <li>{@code open-trapped-chest}: opened a trapped chest.</li>
 * </ul>
 * They run on the player who did it, never on the pet's owner hurting their own pet, and never for players in creative
 * or spectator. Each has a per-player cooldown, {@code hooks.cooldowns}. Damage and opening that another plugin
 * cancelled (a claim, Headcount's pet protection) don't count.
 */
public final class PetAndChestHooks implements Listener {
    public static final String HURT_PET = "hurt-pet";
    public static final String KILL_PET = "kill-pet";
    public static final String OPEN_TRAPPED_CHEST = "open-trapped-chest";

    private final HeadcountPlugin plugin;

    public PetAndChestHooks(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean counts(Player p) {
        return plugin.isLoaded() && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
    }

    /** The owner of a tamed animal, or null if it isn't one. */
    static UUID ownerOf(Entity e) {
        return e instanceof Tameable t && t.isTamed() ? t.getOwnerUniqueId() : null;
    }

    /** {@code <owner> <owner_uuid> <pet> <pet_type> <world> <x> <y> <z>} for a pet. */
    static Vars petVars(Entity pet, UUID owner) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(owner);
        Component name = pet.customName() != null ? pet.customName() : Component.translatable(pet.getType().translationKey());
        return locationVars(pet.getLocation()).put("owner", o.getName() == null ? owner.toString() : o.getName())
                .put("owner_uuid", owner.toString()).put("pet", name).put("pet_type", petType(pet.getType()));
    }

    static String petType(EntityType type) {
        return type.getKey().getKey();
    }

    static Vars locationVars(Location l) {
        return Vars.of("world", l.getWorld() == null ? "" : l.getWorld().getName(), "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0 || !plugin.hooks().hasPlayerHook(HURT_PET)) return;
        UUID owner = ownerOf(event.getEntity());
        if (owner == null) return;
        Player attacker = CombatTracker.responsiblePlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(owner) || !counts(attacker)) return;
        Vars v = petVars(event.getEntity(), owner).put("damage", Math.round(event.getFinalDamage() * 10) / 10.0);
        plugin.hooks().runPlayerHook(attacker, HURT_PET, v, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.hooks().hasPlayerHook(KILL_PET)) return;
        UUID owner = ownerOf(event.getEntity());
        if (owner == null || !(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent last)) return;
        Player killer = CombatTracker.responsiblePlayer(last.getDamager());
        if (killer == null || killer.getUniqueId().equals(owner) || !counts(killer)) return;
        plugin.hooks().runPlayerHook(killer, KILL_PET, petVars(event.getEntity(), owner), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p) || !plugin.hooks().hasPlayerHook(OPEN_TRAPPED_CHEST) || !counts(p)) return;
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof DoubleChest dc) holder = dc.getLeftSide(false);
        if (!(holder instanceof Chest chest) || chest.getType() != Material.TRAPPED_CHEST) return;
        plugin.hooks().runPlayerHook(p, OPEN_TRAPPED_CHEST, locationVars(chest.getLocation()), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.hooks().forget(event.getPlayer().getUniqueId());
    }
}
