package com.gradychastain.headcount.gui;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Safety net: menu display items are tagged, so if one ever ends up outside a menu (a bug, a crash mid-click, another
 * plugin interfering) it is deleted on sight and logged.
 */
public final class GuiItemGuard implements Listener {
    private final HeadcountPlugin plugin;

    public GuiItemGuard(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isGuiItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getPersistentDataContainer().has(plugin.heads().keys().guiItem);
    }

    private void log(Player p, ItemStack item, String where) {
        plugin.audit().log(AuditType.GUI_ITEM_REMOVED, "player", p == null ? null : p.getName() + "(" + p.getUniqueId() + ")",
                "item", item.getType().name(), "where", where);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (GuiManager.gui(event.getView().getTopInventory()) != null) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (isGuiItem(event.getCurrentItem())) {
            log(p, event.getCurrentItem(), "inventory-click");
            event.setCurrentItem(null);
            event.setCancelled(true);
        }
        if (isGuiItem(event.getCursor())) {
            log(p, event.getCursor(), "cursor");
            p.setItemOnCursor(null);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack s = event.getItemDrop().getItemStack();
        if (isGuiItem(s)) {
            log(event.getPlayer(), s, "drop");
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack s = event.getItem().getItemStack();
        if (isGuiItem(s)) {
            log(event.getEntity() instanceof Player p ? p : null, s, "pickup");
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        purge(event.getPlayer(), event.getPlayer().getInventory(), "join");
        purge(event.getPlayer(), event.getPlayer().getEnderChest(), "join-enderchest");
    }

    private void purge(Player p, Inventory inv, String where) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGuiItem(contents[i])) {
                log(p, contents[i], where);
                inv.setItem(i, null);
            }
        }
    }
}
