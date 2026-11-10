package com.gradychastain.headcount.gui;

import com.gradychastain.headcount.HeadcountPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Routes inventory events to Headcount menus. Decisions are made at LOWEST and re-asserted at HIGHEST so another
 * plugin un-cancelling a click cannot open a hole in a menu.
 */
public final class GuiManager implements Listener {
    private final HeadcountPlugin plugin;
    private final Map<Event, Boolean> decisions = new IdentityHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public GuiManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public static Gui gui(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder(false);
        return h instanceof Gui g ? g : null;
    }

    public void open(Gui gui) {
        gui.render();
        gui.viewer().openInventory(gui.getInventory());
    }

    /** Per-player click rate limit for buttons (gui.click-cooldown in config.yml). */
    public boolean clickAllowed(Player p) {
        long cd = plugin.configs().main().getMillis("gui.click-cooldown", 150);
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < cd) return false;
        lastClick.put(p.getUniqueId(), now);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Gui g = gui(event.getView().getTopInventory());
        if (g == null) return;
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(g.viewer()) && !g.allowsOtherViewers()) {
            event.setCancelled(true);
            decisions.put(event, true);
            return;
        }
        boolean cancel;
        try {
            cancel = g.handleClick(event);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error handling a menu click; the click was blocked", e);
            event.setCancelled(true);
            cancel = true;
        }
        decisions.put(event, cancel);
        if (cancel) resync(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickEnforce(InventoryClickEvent event) {
        enforce(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        Gui g = gui(event.getView().getTopInventory());
        if (g == null) return;
        boolean cancel;
        try {
            cancel = g.handleDrag(event);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error handling a menu drag; the drag was blocked", e);
            event.setCancelled(true);
            cancel = true;
        }
        decisions.put(event, cancel);
        if (cancel && event.getWhoClicked() instanceof Player p) resync(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragEnforce(InventoryDragEvent event) {
        enforce(event);
    }

    private void enforce(InventoryInteractEvent event) {
        Boolean cancel = decisions.remove(event);
        if (cancel != null && cancel && !event.isCancelled()) {
            event.setCancelled(true);
            plugin.getLogger().fine("Re-cancelled a menu interaction another plugin had allowed.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Gui g = gui(event.getView().getTopInventory());
        if (g == null) return;
        try {
            g.handleClose(event);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error closing a menu", e);
        }
    }

    /** Re-sends the inventory next tick so the client never shows a cancelled move (important for Bedrock and creative). */
    private void resync(Player p) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.updateInventory();
        });
    }

    /** Closes every open Headcount menu (plugin disable, reload, cycle end). */
    public void closeAll() {
        List<Gui> open = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Gui g = gui(p.getOpenInventory().getTopInventory());
            if (g != null) open.add(g);
        }
        for (Gui g : open) {
            try {
                g.forceClose();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error force-closing a menu", e);
            }
        }
    }

    /** Closes open menus of a given type. */
    public void closeAll(Class<? extends Gui> type) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Gui g = gui(p.getOpenInventory().getTopInventory());
            if (type.isInstance(g)) g.forceClose();
        }
    }
}
