package com.gradychastain.headcount.integration.pvp;

import com.gradychastain.headcount.HeadcountPlugin;
import me.chancesd.pvpmanager.event.PlayerTogglePvPEvent;
import me.chancesd.pvpmanager.player.CombatPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Direct hook into PvPManager 4.x through {@link CombatPlayer}. */
public final class PvPManagerHook implements PvPHook, Listener {
    private final HeadcountPlugin plugin;
    private final Map<UUID, Boolean> locks = new ConcurrentHashMap<>();

    public PvPManagerHook(HeadcountPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private CombatPlayer combat(Player p) {
        try {
            return CombatPlayer.get(p);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override public String name() { return "PvPManager"; }

    @Override
    public Boolean pvpEnabled(Player player) {
        CombatPlayer c = combat(player);
        return c == null ? null : c.hasPvPEnabled();
    }

    @Override
    public Boolean combatTagged(Player player) {
        CombatPlayer c = combat(player);
        return c == null ? null : c.isInCombat();
    }

    @Override
    public Boolean newbie(Player player) {
        CombatPlayer c = combat(player);
        return c == null ? null : c.isNewbie();
    }

    @Override
    public Boolean respawnProtected(Player player) {
        CombatPlayer c = combat(player);
        return c == null ? null : c.hasRespawnProtection();
    }

    @Override
    public Boolean override(Player player) {
        CombatPlayer c = combat(player);
        return c == null ? null : c.hasOverride();
    }

    @Override
    public Long tagSecondsLeft(Player player) {
        CombatPlayer c = combat(player);
        if (c == null || !c.isInCombat()) return c == null ? null : 0L;
        return Math.max(0, c.getTagTimeLeft() / 1000);
    }

    @Override
    public boolean setPvP(Player player, boolean enabled) {
        CombatPlayer c = combat(player);
        if (c == null) return false;
        Boolean lock = locks.remove(player.getUniqueId());
        try {
            c.setPvP(enabled);
        } finally {
            if (lock != null) locks.put(player.getUniqueId(), lock);
        }
        return c.hasPvPEnabled() == enabled;
    }

    @Override
    public void lock(Player player, Boolean state) {
        if (state == null) locks.remove(player.getUniqueId());
        else locks.put(player.getUniqueId(), state);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggle(PlayerTogglePvPEvent event) {
        Boolean locked = locks.get(event.getPlayer().getUniqueId());
        if (locked != null && event.getPvPState() != locked) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "pvp.locked", "state", locked ? "on" : "off");
        }
    }
}
