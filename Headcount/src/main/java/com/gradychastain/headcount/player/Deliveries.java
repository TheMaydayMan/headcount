package com.gradychastain.headcount.player;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Gives items to players. Online players get them in their inventory with the rest dropped at their feet (and a
 * warning); offline players get them queued in the database and delivered on their next join.
 */
public final class Deliveries implements org.bukkit.event.Listener {
    private final HeadcountPlugin plugin;

    public Deliveries(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    /** Gives now if online, else queues. @return number of stacks dropped at the player's feet */
    public int give(UUID uuid, Collection<ItemStack> items, String reason) {
        List<ItemStack> real = items.stream().filter(i -> !Items.isEmpty(i)).toList();
        if (real.isEmpty()) return 0;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline() && !p.isDead()) {
            int dropped = Items.giveOrDrop(p, real);
            if (dropped > 0) plugin.lang().send(p, "items.inventory-full", Vars.of("count", dropped));
            plugin.players().scheduleSave(p);
            return dropped;
        }
        queue(uuid, real, reason);
        return 0;
    }

    public void queue(UUID uuid, Collection<ItemStack> items, String reason) {
        List<byte[]> blobs = new ArrayList<>();
        for (ItemStack i : items) if (!Items.isEmpty(i)) blobs.add(Items.toBytes(i));
        if (blobs.isEmpty()) return;
        long now = System.currentTimeMillis();
        plugin.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pending_items(uuid, item, reason, created_ms) VALUES (?,?,?,?)")) {
                for (byte[] b : blobs) {
                    ps.setString(1, uuid.toString());
                    ps.setBytes(2, b);
                    ps.setString(3, reason);
                    ps.setLong(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
        plugin.audit().log(AuditType.ITEM_DELIVERY, "player", uuid, "queued", blobs.size(), "reason", reason);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !p.isDead()) deliver(p);
        }, 2L);
    }

    /** Delivers everything queued for this player. Rows are deleted in the same transaction they are read. */
    public void deliver(Player player) {
        List<ItemStack> items = plugin.db().sync(c -> {
            List<ItemStack> out = new ArrayList<>();
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT item FROM pending_items WHERE uuid=? ORDER BY id")) {
                    ps.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            try {
                                ItemStack i = Items.fromBytes(rs.getBytes(1));
                                if (i != null) out.add(i);
                            } catch (RuntimeException e) {
                                plugin.getLogger().warning("Skipping an undeliverable queued item for " + player.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM pending_items WHERE uuid=?")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (java.sql.SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            return out;
        });
        if (items.isEmpty()) return;
        for (ItemStack i : items) plugin.heads().refresh(i);
        int dropped = Items.giveOrDrop(player, items);
        plugin.players().scheduleSave(player);   // the queued rows are already deleted
        plugin.lang().send(player, "items.delivered", Vars.of("count", items.size()));
        if (dropped > 0) plugin.lang().send(player, "items.inventory-full", Vars.of("count", dropped));
        plugin.audit().log(AuditType.ITEM_DELIVERY, "player", player.getName() + "(" + player.getUniqueId() + ")", "delivered", items.size(), "dropped", dropped);
    }
}
