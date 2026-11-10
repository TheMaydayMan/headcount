package com.gradychastain.headcount.trade;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.gui.Gui;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One player's trade screen. Own-offer slots accept real items under the trade rules; the other side's slots hold
 * display copies (tagged, never takeable); the middle column has the buttons. Shift-clicking in is done by hand so
 * items can only land in own-offer slots, and double-click collecting is always blocked so it can never pull the
 * other side's copies.
 */
final class TradeGui extends Gui {
    private final TradeManager manager;
    private final TradeSession session;
    private final int[] own;
    private final int[] other;
    private final int[] ownIndex;
    private final MenuLayout layout;
    private boolean syncQueued;
    boolean closingInternally;

    TradeGui(HeadcountPlugin plugin, TradeManager manager, TradeSession session, Player viewer, MenuLayout layout, int[] own, int[] other) {
        super(plugin, viewer);
        this.manager = manager;
        this.session = session;
        this.layout = layout;
        this.own = own;
        this.other = other;
        Player o = session.other(viewer);
        create(layout.rows(6), layout.title(plugin, viewer, Vars.of("other", o.getName()), "<dark_gray>Trade with <other>"));
        ownIndex = new int[inventory.getSize()];
        Arrays.fill(ownIndex, -1);
        for (int i = 0; i < own.length; i++) if (own[i] >= 0 && own[i] < inventory.getSize()) ownIndex[own[i]] = i;
    }

    List<ItemStack> readOwn() {
        List<ItemStack> out = new ArrayList<>();
        for (int s : own) {
            ItemStack i = inventory.getItem(s);
            if (!Items.isEmpty(i)) out.add(i.clone());
        }
        return out;
    }

    /** Removes and returns every real offered item. */
    List<ItemStack> takeOwn() {
        List<ItemStack> out = new ArrayList<>();
        for (int s : own) {
            ItemStack i = inventory.getItem(s);
            if (!Items.isEmpty(i)) out.add(i.clone());
            inventory.setItem(s, null);
        }
        return out;
    }

    void clearMirror() {
        for (int s : other) inventory.setItem(s, null);
    }

    private Vars vars() {
        Player o = session.other(viewer);
        int[] mine = manager.counts(readOwn());
        int[] theirs = manager.counts(session.offer(o));
        return Vars.of("other", o.getName(), "seconds", session.secondsLeft, "my_heads", mine[0], "my_items", mine[1],
                "their_heads", theirs[0], "their_items", theirs[1], "max_heads", manager.rules().maxHeads, "max_items", manager.rules().maxItems);
    }

    @Override
    public void render() {
        Vars v = vars();
        List<Integer> taken = new ArrayList<>();
        for (int s : own) taken.add(s);
        for (int s : other) taken.add(s);
        // Mirror the other side's real offer as tagged display copies.
        Player o = session.other(viewer);
        TradeGui ov = session.view(o);
        for (int i = 0; i < other.length; i++) {
            ItemStack src = ov == null ? null : ov.inventory.getItem(ov.own[i]);
            set(other[i], Items.isEmpty(src) ? null : src.clone(), null);
        }
        if (layout.hasButton("info")) {
            int s = layout.slot("info", 4);
            set(s, layout.buttonItem(plugin, "info", viewer, v), null);
            taken.add(s);
        }
        if (layout.hasButton("confirm")) {
            int s = layout.slot("confirm", 13);
            set(s, layout.buttonItem(plugin, "confirm", session.confirmed(viewer) ? "confirmed" : "unconfirmed", viewer, v), c -> manager.toggleConfirm(viewer));
            taken.add(s);
        }
        if (layout.hasButton("other-status")) {
            int s = layout.slot("other-status", 22);
            set(s, layout.buttonItem(plugin, "other-status", session.confirmed(o) ? "confirmed" : "unconfirmed", viewer, v), null);
            taken.add(s);
        }
        if (layout.hasButton("countdown")) {
            int s = layout.slot("countdown", 31);
            set(s, layout.buttonItem(plugin, "countdown", session.state == TradeSession.State.COUNTDOWN ? "running" : "idle", viewer, v), null);
            taken.add(s);
        }
        if (layout.hasButton("cancel")) {
            int s = layout.slot("cancel", 40);
            set(s, layout.buttonItem(plugin, "cancel", viewer, v), c -> manager.cancel(session, "cancelled-by", viewer));
            taken.add(s);
        }
        if (layout.hasButton("rules")) {
            int s = layout.slot("rules", 49);
            set(s, layout.buttonItem(plugin, "rules", viewer, v), null);
            taken.add(s);
        }
        layout.fill(this, plugin, viewer, v, taken);
    }

    private void queueSync() {
        if (syncQueued) return;
        syncQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            syncQueued = false;
            manager.offerChanged(session, viewer);
        });
    }

    @Override
    public boolean handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (session.finished()) return true;
        int raw = e.getRawSlot();
        int topSize = inventory.getSize();
        InventoryAction action = e.getAction();
        if (raw >= 0 && raw < topSize) {
            int idx = ownIndex[raw];
            if (idx < 0) {
                if (isButtonClick(e.getClick())) {
                    var b = buttons.get(raw);
                    if (b != null && plugin.guis().clickAllowed(viewer)) b.accept(e.getClick());
                }
                return true;
            }
            if (session.locked()) {
                plugin.lang().send(viewer, "trade.locked");
                return true;
            }
            ItemStack current = inventory.getItem(raw);
            switch (action) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE, MOVE_TO_OTHER_INVENTORY -> {
                    e.setCancelled(false);
                    queueSync();
                    return false;
                }
                case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> {
                    ItemStack cursor = e.getCursor();
                    if (!manager.canOffer(viewer, this, cursor, current, action == InventoryAction.PLACE_ONE ? 1 : -1)) return true;
                    e.setCancelled(false);
                    queueSync();
                    return false;
                }
                case HOTBAR_SWAP -> {
                    ItemStack incoming = e.getClick() == ClickType.SWAP_OFFHAND ? viewer.getInventory().getItemInOffHand()
                            : e.getHotbarButton() >= 0 ? viewer.getInventory().getItem(e.getHotbarButton()) : null;
                    if (!Items.isEmpty(incoming) && !manager.canOffer(viewer, this, incoming, current, -1)) return true;
                    e.setCancelled(false);
                    queueSync();
                    return false;
                }
                default -> {
                    return true;
                }
            }
        }
        if (raw < 0) {
            e.setCancelled(false);
            return false;
        }
        // The player's own inventory.
        switch (action) {
            case MOVE_TO_OTHER_INVENTORY -> {
                if (session.locked()) {
                    plugin.lang().send(viewer, "trade.locked");
                    return true;
                }
                ItemStack item = e.getCurrentItem();
                if (Items.isEmpty(item) || !manager.canOffer(viewer, this, item, null, -1)) return true;
                int target = -1;
                for (int s : own) {
                    if (Items.isEmpty(inventory.getItem(s))) {
                        target = s;
                        break;
                    }
                }
                if (target < 0) {
                    plugin.lang().send(viewer, "trade.offer-full");
                    return true;
                }
                inventory.setItem(target, item.clone());
                e.setCurrentItem(null);
                manager.offerChanged(session, viewer);
                return true;
            }
            case COLLECT_TO_CURSOR -> {
                return true;
            }
            default -> {
                e.setCancelled(false);
                return false;
            }
        }
    }

    @Override
    public boolean handleDrag(InventoryDragEvent e) {
        int topSize = inventory.getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (!touchesTop) return false;
        e.setCancelled(true);
        if (session.locked() || session.finished()) return true;
        for (int s : e.getRawSlots()) if (s < topSize && ownIndex[s] < 0) return true;
        // Validate the offer as it would be after the drag.
        List<ItemStack> after = new ArrayList<>();
        for (int s : own) {
            ItemStack placed = null;
            for (Map.Entry<Integer, ItemStack> en : e.getNewItems().entrySet()) if (en.getKey() == s) placed = en.getValue();
            ItemStack cur = placed != null ? placed : inventory.getItem(s);
            if (!Items.isEmpty(cur)) after.add(cur);
        }
        if (!manager.validOffer(viewer, after, true) || !manager.itemAllowed(viewer, e.getOldCursor(), true)) return true;
        e.setCancelled(false);
        queueSync();
        return false;
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (closingInternally || session.finished()) return;
        // Dying closes the screen; the death handler returns the offer into the death drops instead.
        if (e.getReason() == InventoryCloseEvent.Reason.DEATH) return;
        manager.cancel(session, e.getReason() == InventoryCloseEvent.Reason.DISCONNECT ? "disconnected" : "closed", viewer);
    }

    @Override
    public void forceClose() {
        manager.cancel(session, "shutdown", null);
    }

    int[] ownSlots() {
        return own;
    }
}
