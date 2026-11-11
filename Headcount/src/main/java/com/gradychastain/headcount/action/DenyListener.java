package com.gradychastain.headcount.action;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.types.DenyAction;
import com.gradychastain.headcount.integration.moderation.ModerationHook;
import com.gradychastain.headcount.status.CombatTracker;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces deny actions, plus Headcount's own mutes and jails when no moderation plugin handles them. Chat runs
 * off the main thread, so who may chat is kept in a thread-safe snapshot refreshed every second.
 */
public final class DenyListener implements Listener {
    private final HeadcountPlugin plugin;
    private final Map<UUID, String> chatBlocked = new ConcurrentHashMap<>();

    public DenyListener(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean denied(Player p, String category, String detail) {
        return plugin.actions().isDenied(p, category, detail);
    }

    private boolean vanillaMuted(Player p) {
        ModerationHook h = plugin.integrations().moderation(ModerationHook.Kind.MUTE);
        return h != null && h.enforcedByHeadcount(ModerationHook.Kind.MUTE) && plugin.actions().hasActiveType(p.getUniqueId(), "mute");
    }

    private boolean vanillaJailed(Player p) {
        ModerationHook h = plugin.integrations().moderation(ModerationHook.Kind.JAIL);
        return h != null && h.enforcedByHeadcount(ModerationHook.Kind.JAIL) && plugin.actions().hasActiveType(p.getUniqueId(), "jail");
    }

    /** Refreshes who cannot chat (main thread). */
    public void refreshChat() {
        chatBlocked.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (vanillaMuted(p)) chatBlocked.put(p.getUniqueId(), "mute");
            else if (!p.hasPermission("headcount.bypass.deny.chat")) {
                for (var e : plugin.actions().handlers(p.getUniqueId(), DenyAction.Handler.class)) {
                    if (e.getKey().denies("chat", null)) {
                        chatBlocked.put(p.getUniqueId(), "deny");
                        break;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String why = chatBlocked.get(event.getPlayer().getUniqueId());
        if (why == null) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.lang().send(p, why.equals("mute") ? "moderation.muted-chat" : "deny.chat"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String label = DenyAction.Handler.normalize(event.getMessage());
        if (label.equals("hc") || label.equals("headcount") || label.equals("hca") || label.equals("hcadmin")) {
            if (p.hasPermission("headcount.admin")) return;
        }
        if (vanillaJailed(p)) {
            List<String> allowed = plugin.integrations().includedJail().getStringList("allowed-commands");
            if (allowed.stream().noneMatch(a -> DenyAction.Handler.normalize(a).equals(label))) {
                event.setCancelled(true);
                plugin.lang().send(p, "moderation.jailed-command");
                return;
            }
        }
        if (vanillaMuted(p)) {
            List<String> blocked = plugin.integrations().includedMute().getStringList("blocked-commands");
            if (blocked.stream().anyMatch(b -> DenyAction.Handler.normalize(b).equals(label))) {
                event.setCancelled(true);
                plugin.lang().send(p, "moderation.muted-chat");
                return;
            }
        }
        if (denied(p, "commands", label)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), "break", event.getBlock().getType().name())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), "place", event.getBlockPlaced().getType().name())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && denied(p, "interact", event.getClickedBlock() == null ? null : event.getClickedBlock().getType().name())) {
            event.setCancelled(true);
            return;
        }
        if (event.getItem() != null && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (denied(p, "items", event.getItem().getType().name())) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p && denied(p, "ender-pearl", null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && denied(p, "ender-pearl", null)) {
            event.setCancelled(true);
            return;
        }
        if (event.getTo().getWorld() != null && !event.getTo().getWorld().equals(event.getFrom().getWorld())
                && denied(p, "worlds", event.getTo().getWorld().getName())) {
            event.setCancelled(true);
            return;
        }
        if (vanillaJailed(p) && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN && event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent event) {
        if (event.isFlying() && denied(event.getPlayer(), "fly", null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player p && denied(p, "elytra", null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent event) {
        if (denied(event.getPlayer(), "elytra", null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (denied(event.getPlayer(), "drop", event.getItemDrop().getItemStack().getType().name())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && plugin.actions().handlers(p.getUniqueId(), DenyAction.Handler.class).stream()
                .anyMatch(e -> e.getKey().denies("pickup", null)) && !p.hasPermission("headcount.bypass.deny.pickup")) {
            event.setCancelled(true); // no message: pickups are attempted every tick
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = CombatTracker.responsiblePlayer(event.getDamager());
        if (attacker != null && denied(attacker, "attack", event.getEntityType().name().toLowerCase(Locale.ROOT))) event.setCancelled(true);
    }
}
