package com.gradychastain.headcount.gui;

import com.gradychastain.headcount.HeadcountPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base for every Headcount inventory. The default policy is a locked menu: every click and drag is cancelled, in the
 * menu and in the player's own inventory, and only plain left/right/shift clicks on buttons do anything. That rules
 * out shift-clicking items in, number-key and offhand swaps, double-click collecting display items, drags, drop
 * keys, middle-click cloning and bundle interactions. The vault and trade screens override this with their own
 * validated rules.
 */
public abstract class Gui implements InventoryHolder {
    protected final HeadcountPlugin plugin;
    protected final Player viewer;
    protected Inventory inventory;
    protected final Map<Integer, Consumer<ClickType>> buttons = new HashMap<>();

    protected Gui(HeadcountPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected void create(int rows, Component title) {
        int r = Math.max(1, Math.min(6, rows));
        inventory = Bukkit.createInventory(this, r * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    /** True for inventories several players can have open at once (a vault viewed by its owner and an admin). */
    public boolean allowsOtherViewers() {
        return false;
    }

    /** Fills the inventory. Called before opening and whenever the menu refreshes. */
    public abstract void render();

    public void open() {
        plugin.guis().open(this);
    }

    /** Puts a display item (marked so it can never be kept) with an optional click action. */
    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        if (item != null && !item.getType().isAir()) {
            item.editMeta(m -> m.getPersistentDataContainer().set(plugin.heads().keys().guiItem, PersistentDataType.BYTE, (byte) 1));
        }
        inventory.setItem(slot, item);
        if (action == null) buttons.remove(slot);
        else buttons.put(slot, action);
    }

    protected void clearButtons() {
        buttons.clear();
        inventory.clear();
    }

    public static boolean isButtonClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }

    /** Default locked-menu click handling. Returns whether the event must stay cancelled. */
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return true;
        if (!isButtonClick(event.getClick())) return true;
        Consumer<ClickType> action = buttons.get(event.getRawSlot());
        if (action != null && plugin.guis().clickAllowed(viewer)) {
            try {
                action.accept(event.getClick());
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Menu button failed", e);
            }
        }
        return true;
    }

    /** Default: no drags at all. Returns whether the event must stay cancelled. */
    public boolean handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
        return true;
    }

    public void handleClose(InventoryCloseEvent event) {}

    /** Called when the plugin disables or reloads while this menu is open. */
    public void forceClose() {
        viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
    }
}
