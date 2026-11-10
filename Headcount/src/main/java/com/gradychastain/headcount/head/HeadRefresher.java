package com.gradychastain.headcount.head;

import com.gradychastain.headcount.HeadcountPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
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
 */
public final class HeadRefresher implements Listener {
    private final HeadcountPlugin plugin;

    public HeadRefresher(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    /** Refreshes one stack (and the heads inside a shulker box or bundle). @return true if it changed */
    public boolean refresh(ItemStack item, int depth) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() == Material.PLAYER_HEAD) return plugin.heads().refresh(item);
        if (depth > 0) return false;
        if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
            boolean changed = false;
            ItemStack[] contents = box.getInventory().getContents();
            for (ItemStack inner : contents) changed |= refresh(inner, depth + 1);
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
            for (ItemStack i : inner) changed |= refresh(i, depth + 1);
            if (changed) {
                bundle.setItems(inner);
                item.setItemMeta(bundle);
            }
            return changed;
        }
        return false;
    }

    public void refresh(Inventory inv) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (refresh(contents[i], 0)) inv.setItem(i, contents[i]);
        }
    }

    public void refresh(Player player) {
        refresh(player.getInventory());
        refresh(player.getEnderChest());
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
        refresh(event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack s = event.getItem().getItemStack();
        if (refresh(s, 0)) event.getItem().setItemStack(s);
    }
}
