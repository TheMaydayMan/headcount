package com.gradychastain.headcount.kill;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.types.HeadcountActions;
import com.gradychastain.headcount.api.event.KillHeadDropEvent;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Killing a player drops a newly made head of the victim, of the current cycle, at the victim's location (never
 * taken from their vault), once per killer per victim per cycle. The killer is whoever vanilla credits with the
 * kill, so projectiles and knock-into-lava kills count. The drop is separate from the death drops, so it happens
 * whether or not keepInventory is on.
 */
public final class KillManager implements Listener {
    private final HeadcountPlugin plugin;
    private final Set<String> claimed = new HashSet<>();

    public KillManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        claimed.clear();
        int cycle = plugin.cycles().currentId();
        if (cycle <= 0) return;
        claimed.addAll(plugin.db().sync(c -> {
            Set<String> out = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT killer, victim FROM kill_drops WHERE cycle=?")) {
                ps.setInt(1, cycle);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1) + ">" + rs.getString(2));
                }
            }
            return out;
        }));
    }

    public void resetCycle() {
        claimed.clear();
    }

    private Cfg cfg() {
        return plugin.configs().main().getSection("kills");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        Cycle cycle = plugin.cycles().current();
        if (killer == null || killer.equals(victim) || cycle == null || plugin.cycles().isProcessing()) return;
        Cfg cfg = cfg();
        Location at = victim.getLocation();
        Vars vars = Vars.of("killer", killer.getName(), "victim", victim.getName(), "cycle", cycle.id());

        // Bounties pay out on any credited kill.
        for (var e : plugin.actions().handlers(victim.getUniqueId(), HeadcountActions.Bounty.class)) {
            HeadcountActions.Bounty b = e.getKey();
            if (b.points != 0) plugin.points().change(killer.getUniqueId(), b.points, false, "bounty:" + victim.getName(), "bounty");
            plugin.actions().run(b.rewards, killer.getUniqueId(), vars.copy().put("points", b.points), "bounty:" + victim.getUniqueId());
            plugin.lang().send(killer, "bounty.claimed", vars.copy().put("points", b.points));
            if (b.announce) {
                for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(killer)) plugin.lang().send(p, "bounty.claimed-broadcast", vars.copy().put("points", b.points));
            }
            b.use(plugin, e.getValue());
        }

        if (!cfg.getBool("enabled", true)) return;
        List<String> worlds = cfg.getStringList("disabled-worlds");
        if (worlds.stream().anyMatch(w -> w.equalsIgnoreCase(at.getWorld().getName()))) return;
        for (String s : cfg.getStringList("killer-blocked-statuses")) {
            if (plugin.statuses().has(killer, s)) return;
        }
        String key = killer.getUniqueId() + ">" + victim.getUniqueId();
        boolean once = cfg.getBool("once-per-victim-per-cycle", true);
        if (once && claimed.contains(key)) {
            plugin.lang().send(killer, "kills.already-claimed", vars);
            return;
        }
        var protections = plugin.actions().handlers(victim.getUniqueId(), HeadcountActions.HeadProtection.class);
        if (!protections.isEmpty()) {
            var pr = protections.getFirst();
            pr.getKey().use(plugin, pr.getValue());
            plugin.lang().send(killer, "kills.protected", vars);
            plugin.audit().log(AuditType.KILL_DROP, "killer", killer.getName() + "(" + killer.getUniqueId() + ")",
                    "victim", victim.getName() + "(" + victim.getUniqueId() + ")", "result", "head-protected", "cycle", cycle.id());
            return;
        }
        int amount = Math.max(1, cfg.getInt("heads-per-kill", 1));
        for (var e : plugin.actions().handlers(victim.getUniqueId(), HeadcountActions.ExtraHeads.class)) {
            amount += e.getKey().amount;
            e.getKey().use(plugin, e.getValue());
        }
        KillHeadDropEvent dropEvent = new KillHeadDropEvent(killer, victim, amount);
        Bukkit.getPluginManager().callEvent(dropEvent);
        if (dropEvent.isCancelled() || dropEvent.getAmount() <= 0) return;
        List<ItemStack> items = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < dropEvent.getAmount(); i++) {
            HeadData h = plugin.heads().newHead(victim.getUniqueId(), victim.getName(), cycle.id(), "kill", killer.getUniqueId(), "kill:" + killer.getName());
            items.add(plugin.heads().createItem(h));
            ids.add(h.id().toString());
        }
        for (ItemStack item : items) {
            var drop = Items.dropNaturally(at, item);
            if (drop != null && cfg.getMillis("killer-pickup-only", 0) > 0) {
                drop.setOwner(killer.getUniqueId());
                long ms = cfg.getMillis("killer-pickup-only", 0);
                var d = drop;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (d.isValid()) d.setOwner(null);
                }, Math.max(1, ms / 50));
            }
        }
        if (once) {
            claimed.add(key);
            int cycleId = cycle.id();
            long now = System.currentTimeMillis();
            plugin.db().write(c -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO kill_drops(cycle, killer, victim, ms) VALUES (?,?,?,?)")) {
                    ps.setInt(1, cycleId);
                    ps.setString(2, killer.getUniqueId().toString());
                    ps.setString(3, victim.getUniqueId().toString());
                    ps.setLong(4, now);
                    ps.executeUpdate();
                }
            });
        }
        PlayerData kd = plugin.players().get(killer.getUniqueId());
        if (kd != null && kd.stats != null) kd.stats.kills++;
        plugin.playerStore().addStat(cycle.id(), killer.getUniqueId(), "kills", 1);
        PlayerData vd = plugin.players().get(victim.getUniqueId());
        if (vd != null && vd.stats != null) vd.stats.deaths++;
        plugin.playerStore().addStat(cycle.id(), victim.getUniqueId(), "deaths", 1);
        plugin.audit().log(AuditType.KILL_DROP, "killer", killer.getName() + "(" + killer.getUniqueId() + ")",
                "victim", victim.getName() + "(" + victim.getUniqueId() + ")", "heads", String.join(",", ids), "cycle", cycle.id(),
                "location", at.getWorld().getName() + "," + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ(),
                "cause", victim.getLastDamageCause() == null ? "unknown" : victim.getLastDamageCause().getCause().name().toLowerCase(Locale.ROOT));
        plugin.lang().send(killer, "kills.dropped", vars.copy().put("amount", items.size()));
        plugin.lang().send(victim, "kills.victim", vars.copy().put("amount", items.size()));
    }
}
