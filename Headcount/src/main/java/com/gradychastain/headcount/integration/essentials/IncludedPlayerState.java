package com.gradychastain.headcount.integration.essentials;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.integration.moderation.ModerationHook;
import com.gradychastain.headcount.util.Cfg;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Headcount's own player states (integrations.yml player-state.provider: included): AFK after a configurable idle
 * time (moving, looking around, chatting, commands and clicks count as activity), vanished from the "vanished"
 * metadata most vanish plugins set, god mode from the player's invulnerable flag, and jailed / muted from Headcount's
 * own jail and mute actions (or the moderation plugin, when it can tell).
 */
public final class IncludedPlayerState implements PlayerStateHook, Listener {
    private final HeadcountPlugin plugin;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Set<UUID> afk = new HashSet<>();
    private boolean active;
    private BukkitTask task;

    public IncludedPlayerState(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private Cfg cfg() {
        return plugin.configs().integrations().getSection("player-state.included");
    }

    public void setActive(boolean on) {
        active = on;
        if (task != null) task.cancel();
        task = null;
        afk.clear();
        if (on) {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) lastActive.put(p.getUniqueId(), now);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
        }
    }

    @Override public String name() { return "included"; }

    @Override
    @SuppressWarnings("deprecation") // metadata and the invulnerable flag are what vanish and god-mode plugins still set
    public Boolean check(Player p, String what) {
        if (!active) return null;
        return switch (what.toLowerCase(Locale.ROOT)) {
            case "afk" -> afk.contains(p.getUniqueId());
            case "vanished", "vanish" -> {
                for (MetadataValue v : p.getMetadata("vanished")) if (v.asBoolean()) yield true;
                yield false;
            }
            case "god", "godmode" -> p.isInvulnerable();
            case "jailed", "jail" -> plugin.actions().hasActiveType(p.getUniqueId(), "jail");
            case "muted", "mute" -> {
                ModerationHook h = plugin.integrations().moderation(ModerationHook.Kind.MUTE);
                Boolean v = h == null || h.enforcedByHeadcount(ModerationHook.Kind.MUTE) ? null : h.isMuted(p.getUniqueId());
                yield v != null ? v : plugin.actions().hasActiveType(p.getUniqueId(), "mute");
            }
            default -> null;
        };
    }

    private void tick() {
        long after = cfg().getMillis("afk.after", 300_000);
        if (after <= 0) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            long last = lastActive.computeIfAbsent(p.getUniqueId(), k -> now);
            if (now - last >= after && afk.add(p.getUniqueId())) changed(p, true);
        }
    }

    private void activity(Player p) {
        if (!active) return;
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
        if (afk.contains(p.getUniqueId())) {
            // Back from AFK on the main thread (chat arrives async).
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (afk.remove(p.getUniqueId())) changed(p, false);
            });
        }
    }

    private void changed(Player p, boolean nowAfk) {
        plugin.statuses().invalidate(p.getUniqueId());
        String announce = cfg().getString("afk.announce", "self").toLowerCase(Locale.ROOT);
        String key = nowAfk ? "player-state.afk-on" : "player-state.afk-off";
        switch (announce) {
            case "everyone" -> Bukkit.getOnlinePlayers().forEach(o -> plugin.lang().send(o, key + "-broadcast", "player", p.getName()));
            case "self" -> plugin.lang().send(p, key, "player", p.getName());
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        boolean moved = from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
        boolean looked = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        if (moved || (looked && cfg().getBool("afk.looking-counts", true))) activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastActive.remove(event.getPlayer().getUniqueId());
        afk.remove(event.getPlayer().getUniqueId());
    }
}
