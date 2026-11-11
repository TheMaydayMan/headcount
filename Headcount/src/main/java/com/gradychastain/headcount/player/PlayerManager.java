package com.gradychastain.headcount.player;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.event.PlayerBecameActiveEvent;
import com.gradychastain.headcount.api.event.PlayerCycleJoinEvent;
import com.gradychastain.headcount.api.event.PlayerFirstJoinEvent;
import com.gradychastain.headcount.cycle.Cycle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads and caches player data, tracks playtime and activity, and runs the join sequence:
 * delivered per-player cycle hooks, then first join ever, then first join of the cycle, then becoming active,
 * then every join.
 */
public final class PlayerManager implements Listener {
    private final HeadcountPlugin plugin;
    private final PlayerStore store;
    private final Map<UUID, PlayerData> online = new HashMap<>();
    private final Map<UUID, PlayerData> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingSaves = new HashMap<>();

    public PlayerManager(HeadcountPlugin plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public PlayerStore store() {
        return store;
    }

    public PlayerData get(UUID uuid) {
        return online.get(uuid);
    }

    /**
     * Cached data for a player who is online or currently logging in (preloaded but not joined yet). Changes made to
     * offline players must also be applied here so a login in progress does not miss them.
     */
    public PlayerData loaded(UUID uuid) {
        PlayerData d = online.get(uuid);
        return d != null ? d : preloaded.get(uuid);
    }

    public PlayerData get(Player p) {
        PlayerData d = online.get(p.getUniqueId());
        if (d == null) d = loadNow(p); // join raced with something; load synchronously
        return d;
    }

    public Collection<PlayerData> onlineData() {
        return online.values();
    }

    private PlayerData loadNow(Player p) {
        int cycle = plugin.cycles().currentId();
        PlayerData d = plugin.db().sync(c -> store.load(c, p.getUniqueId(), p.getName(), cycle));
        online.put(p.getUniqueId(), d);
        return d;
    }

    // ---------------------------------------------------------------- login / join / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            int cycle = plugin.cycles().currentId();
            PlayerData d = plugin.db().sync(c -> store.load(c, event.getUniqueId(), event.getName(), cycle));
            preloaded.put(event.getUniqueId(), d);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not preload Headcount data for " + event.getName() + "; it will load on join.", e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer(), true);
    }

    /** @param realJoin false when loading players already online (plugin enable); skips the every-join hook */
    private void handleJoin(Player p, boolean realJoin) {
        PlayerData d = preloaded.remove(p.getUniqueId());
        Cycle cycle = plugin.cycles().current();
        if (d == null || (cycle != null && d.stats != null && d.stats.cycle != cycle.id())) d = loadNow(p);
        else online.put(p.getUniqueId(), d);
        plugin.combat().markJoined(p.getUniqueId());
        long now = System.currentTimeMillis();

        boolean firstEver = !d.exists;
        if (firstEver) {
            store.insertPlayer(p.getUniqueId(), p.getName(), now);
            d.exists = true;
            // New players start with the sidebar shown or hidden as config.yml sidebar.shown-for-new-players says.
            if (plugin.configs().main().getBool("sidebar.shown-for-new-players", true)) {
                d.sidebar = true;
                store.set(p.getUniqueId(), "sidebar", true);
            }
            d.firstJoin = now;
            d.name = p.getName();
        } else if (!p.getName().equals(d.name)) {
            d.name = p.getName();
            store.updateName(p.getUniqueId(), p.getName());
        }
        d.lastJoin = now;
        store.set(p.getUniqueId(), "last_join", now);
        boolean bedrock = plugin.integrations().isBedrock(p.getUniqueId());
        if (bedrock != d.bedrock) {
            d.bedrock = bedrock;
            store.set(p.getUniqueId(), "bedrock", bedrock);
        }
        plugin.heads().cacheTexture(p);

        boolean firstOfCycle = false;
        if (cycle != null) {
            if (d.setupCycle != cycle.id()) plugin.vaults().setupForCycle(p.getUniqueId(), p.getName(), cycle, d);
            if (d.stats == null) {
                d.stats = new CycleStats(cycle.id());
                d.stats.firstJoinMs = now;
                store.ensureStats(cycle.id(), p.getUniqueId(), now);
                firstOfCycle = true;
            }
        }
        d.sessionMark = now;

        final boolean fe = firstEver, fc = firstOfCycle;
        long delay = Math.max(0, plugin.configs().main().getMillis("hooks.join-delay", 1000) / 50);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) afterJoin(p, fe, fc, realJoin);
        }, delay);
    }

    private void afterJoin(Player p, boolean firstEver, boolean firstOfCycle, boolean realJoin) {
        PlayerData d = get(p);
        try {
            plugin.trades().recover(p);
            plugin.deliveries().deliver(p);
            plugin.actions().onJoin(p);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Error restoring Headcount state for " + p.getName(), e);
        }
        plugin.hooks().deliverPending(p);
        if (firstEver) {
            Bukkit.getPluginManager().callEvent(new PlayerFirstJoinEvent(p));
            plugin.hooks().runPlayerHook(p, "first-join");
        }
        Cycle cycle = plugin.cycles().current();
        if (firstOfCycle && cycle != null && d.stats != null && d.stats.cycle == cycle.id()) {
            Bukkit.getPluginManager().callEvent(new PlayerCycleJoinEvent(p, cycle.id()));
            plugin.hooks().runPlayerHook(p, "first-join-cycle");
        }
        checkActive(p);
        if (realJoin) plugin.hooks().runPlayerHook(p, "join");
        plugin.sidebar().onJoin(p);
        plugin.dupes().queueScan(p, "join");
    }

    /**
     * Saves the player's data file about a second after the first unsaved change, so a crash cannot roll back only
     * one side of a transfer (an item Headcount stored that is still in the saved inventory, or the reverse). Later
     * changes within that second go into the same save, and continuous changes cannot postpone it.
     */
    public void scheduleSave(Player p) {
        UUID id = p.getUniqueId();
        if (pendingSaves.containsKey(id)) return;
        pendingSaves.put(id, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSaves.remove(id);
            if (p.isOnline()) p.saveData();
        }, 20L));
    }

    /** Treats players online during a rollover as joining the new cycle. Called after cycle-start hooks. */
    public void onCycleStarted(Cycle cycle) {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = online.get(p.getUniqueId());
            if (d == null) continue;
            if (d.setupCycle != cycle.id()) plugin.vaults().setupForCycle(p.getUniqueId(), p.getName(), cycle, d);
            d.stats = new CycleStats(cycle.id());
            d.stats.firstJoinMs = now;
            d.sessionMark = now;
            d.purchasesThisCycle.clear();
            store.ensureStats(cycle.id(), p.getUniqueId(), now);
            Bukkit.getPluginManager().callEvent(new PlayerCycleJoinEvent(p, cycle.id()));
            plugin.hooks().runPlayerHook(p, "first-join-cycle");
            checkActive(p);
        }
    }

    /** Flushes playtime into the ending cycle for everyone online. Called before the cycle-end tally. */
    public void flushPlaytime() {
        long now = System.currentTimeMillis();
        for (PlayerData d : online.values()) {
            if (d.stats == null) continue;
            long add = Math.max(0, now - d.sessionMark);
            d.sessionMark = now;
            if (add > 0) {
                d.stats.playtimeMs += add;
                store.addStat(d.stats.cycle, d.uuid, "playtime_ms", add);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PlayerData d = online.get(id);
        if (d != null) {
            long now = System.currentTimeMillis();
            if (d.stats != null) {
                long add = Math.max(0, now - d.sessionMark);
                d.stats.playtimeMs += add;
                if (add > 0) store.addStat(d.stats.cycle, id, "playtime_ms", add);
            }
            store.set(id, "last_quit", now);
        }
        // Remove one tick later so other quit handlers (trades, actions) can still read the data.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPlayer(id) == null) online.remove(id);
        });
    }

    /** Periodic playtime flush and activity check (activity.check-interval). */
    public void tick() {
        flushPlaytime();
        for (Player p : Bukkit.getOnlinePlayers()) checkActive(p);
    }

    public long activeThresholdMs() {
        return plugin.configs().main().getMillis("activity.min-playtime", 7_200_000);
    }

    /** Marks the player active for the cycle once they reach the configured playtime. */
    public void checkActive(Player p) {
        PlayerData d = online.get(p.getUniqueId());
        Cycle cycle = plugin.cycles().current();
        if (d == null || d.stats == null || cycle == null || d.stats.cycle != cycle.id() || d.stats.active) return;
        if (plugin.cycles().isProcessing()) return;
        long played = d.stats.playtimeMs + Math.max(0, System.currentTimeMillis() - d.sessionMark);
        if (played < activeThresholdMs()) return;
        d.stats.active = true;
        store.setStat(cycle.id(), p.getUniqueId(), "active", 1);
        Bukkit.getPluginManager().callEvent(new PlayerBecameActiveEvent(p, cycle.id()));
        plugin.hooks().runPlayerHook(p, "became-active");
    }

    // ---------------------------------------------------------------- flags and names

    public boolean hasFlag(UUID uuid, String flag) {
        PlayerData d = online.get(uuid);
        String f = flag.toLowerCase(Locale.ROOT);
        return d != null ? d.flags.contains(f) : store.hasFlagStored(uuid, f);
    }

    public void setFlag(UUID uuid, String flag, boolean on) {
        String f = flag.toLowerCase(Locale.ROOT);
        PlayerData d = online.get(uuid);
        if (d != null) {
            if (on) d.flags.add(f);
            else d.flags.remove(f);
        }
        store.setFlag(uuid, f, on);
        plugin.statuses().invalidate(uuid);
    }

    public String name(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        PlayerData d = online.get(uuid);
        if (d != null) return d.name;
        String n = store.nameOf(uuid);
        if (n != null) return n;
        String off = Bukkit.getOfflinePlayer(uuid).getName();
        return off == null ? uuid.toString().substring(0, 8) : off;
    }

    /** Resolves a player name: online players first, then everyone who has ever joined. */
    public UUID resolve(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        return store.findByName(name);
    }

    public List<String> complete(String prefix, boolean includeOffline) {
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) out.add(p.getName());
        if (includeOffline && out.size() < 20) {
            for (String n : store.knownNames(prefix, 20)) if (!out.contains(n)) out.add(n);
        }
        return out;
    }

    /** Hard reset: forgets every cached player. {@link #loadOnline()} then sets up the players online as new. */
    public void forgetAll() {
        online.clear();
        preloaded.clear();
    }

    /** Loads data for players already online (plugin enabled while players are connected). */
    public void loadOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!online.containsKey(p.getUniqueId())) handleJoin(p, false);
        }
    }
}
