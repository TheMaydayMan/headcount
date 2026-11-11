package com.gradychastain.headcount.head;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps economy head names and lore in sync with their state. Expired heads keep all their data and still work as
 * normal heads; only their text changes. Refreshing is lazy: inventories are touched when players join, open
 * containers or pick items up, and online players are refreshed at each cycle start. Shulker boxes and bundles are
 * refreshed one level deep.
 * <p>
 * Heads from before a {@code /hca reset cycle} or {@code /hca reset hard} that chose to delete them are deleted on the
 * same occasions (and when they would drop as an item); after a reset that kept them they only show as defunct.
 */
public final class HeadRefresher implements Listener {
    private final HeadcountPlugin plugin;

    public HeadRefresher(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Deletes a stack if it is a head from before a reset. @param where for the audit log
     * @return true if it was deleted (the caller removes it from where it was)
     */
    public boolean removeIfVoid(ItemStack item, String where) {
        if (!plugin.heads().shouldRemove(item)) return false;
        HeadData d = plugin.heads().read(item);
        plugin.audit().log(AuditType.HEAD_DELETE, "head", d.id(), "owner", d.ownerName() + "(" + d.owner() + ")", "cycle", d.cycle(),
                "amount", item.getAmount(), "reason", "reset", "where", where);
        return true;
    }

    /** Refreshes one stack (and the heads inside a shulker box or bundle, deleting heads from before a reset there). @return true if it changed */
    public boolean refresh(ItemStack item, int depth) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() == Material.PLAYER_HEAD) return plugin.heads().refresh(item);
        if (depth > 0) return false;
        if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
            boolean changed = false;
            ItemStack[] contents = box.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (removeIfVoid(contents[i], "shulker-box")) {
                    contents[i] = null;
                    changed = true;
                } else {
                    changed |= refresh(contents[i], depth + 1);
                }
            }
            if (changed) {
                box.getInventory().setContents(contents);
                bsm.setBlockState(box);
                item.setItemMeta(bsm);
            }
            return changed;
        }
        if (item.getItemMeta() instanceof BundleMeta bundle && bundle.hasItems()) {
            boolean changed = false;
            List<ItemStack> inner = new ArrayList<>(bundle.getItems());
            changed |= inner.removeIf(i -> removeIfVoid(i, "bundle"));
            for (ItemStack i : inner) changed |= refresh(i, depth + 1);
            if (changed) {
                bundle.setItems(inner);
                item.setItemMeta(bundle);
            }
            return changed;
        }
        return false;
    }

    public void refresh(Inventory inv, String where) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (removeIfVoid(contents[i], where)) inv.setItem(i, null);
            else if (refresh(contents[i], 0)) inv.setItem(i, contents[i]);
        }
    }

    public void refresh(Player player) {
        String who = player.getName() + "(" + player.getUniqueId() + ")";
        refresh(player.getInventory(), "inventory:" + who);
        refresh(player.getEnderChest(), "ender-chest:" + who);
        if (removeIfVoid(player.getItemOnCursor(), "cursor:" + who)) player.setItemOnCursor(null);
    }

    public void refreshOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) refresh(p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof com.gradychastain.headcount.gui.Gui) return; // Headcount menus manage their own items
        var loc = event.getInventory().getLocation();
        refresh(event.getInventory(), "container:" + event.getInventory().getType().name().toLowerCase(java.util.Locale.ROOT)
                + (loc == null || loc.getWorld() == null ? "" : "@" + loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack s = event.getItem().getItemStack();
        if (removeIfVoid(s, "pickup:" + event.getEntity().getName())) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }
        if (refresh(s, 0)) event.getItem().setItemStack(s);
    }

    /** A head from before a reset never becomes a dropped item (a broken placed head, a death drop, ...). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        var l = event.getLocation();
        if (removeIfVoid(event.getEntity().getItemStack(), "drop@" + l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ())) {
            event.setCancelled(true);
        }
    }
}
