package com.gradychastain.headcount.vault;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.gui.Gui;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One page of one vault, shared live by everyone viewing it (the owner and any admins). Rules:
 * <ul>
 *   <li>only current-cycle economy heads, one per slot, whose ID is not already in some vault, may go in;</li>
 *   <li>movement needs edit rights and no blocking status (vault-move gate) for non-admin viewers;</li>
 *   <li>allowed: pick up, place, swap with cursor, number-key/offhand swap, shift-click either way, single-slot drags;</li>
 *   <li>never allowed in vault slots: drop keys, middle-click clone, double-click collect, bundle interactions.</li>
 * </ul>
 * Every click first syncs the inventory into the vault data, and a sync runs again on the next tick and on close,
 * so the database always mirrors what is really in the slots.
 */
final class VaultGui extends Gui {
    private record Mode(boolean admin, boolean edit) {}

    static final int PAGED_SLOTS = 45;

    private final VaultManager manager;
    private final VaultData data;
    private final int page;
    private final String ownerName;
    private final int[] storage; // inventory slot -> vault slot, or -1
    private final boolean paged;
    private final Map<UUID, Mode> viewers = new HashMap<>();
    private boolean syncQueued;
    private boolean closing;
    /** False until the inventory has been drawn once; syncing before that would read empty slots as removals. */
    private boolean rendered;
    private Player clicker;

    static int pages(int size) {
        return size <= 54 ? 1 : (int) Math.ceil(size / (double) PAGED_SLOTS);
    }

    VaultGui(HeadcountPlugin plugin, Player opener, VaultData data, int page, String ownerName) {
        super(plugin, opener);
        this.manager = plugin.vaults();
        this.data = data;
        this.page = page;
        this.ownerName = ownerName;
        this.paged = data.size > 54;
        int rows = paged ? 6 : Math.max(1, (int) Math.ceil(data.size / 9.0));
        VaultTally t = data.tally();
        Vars vars = Vars.of("owner", ownerName, "page", page + 1, "pages", pages(data.size), "unique", t.unique(), "total", t.total(), "size", data.size);
        create(rows, layout().title(plugin, opener, vars, "<dark_gray><owner>'s Head Vault"));
        inventory.setMaxStackSize(1);
        storage = new int[rows * 9];
        Arrays.fill(storage, -1);
        int perPage = paged ? PAGED_SLOTS : rows * 9;
        for (int i = 0; i < perPage; i++) {
            int global = page * perPage + i;
            if (global < data.size) storage[i] = global;
        }
    }

    private MenuLayout layout() {
        return new MenuLayout(plugin.configs().main().getSection("vault.gui"));
    }

    VaultData data() {
        return data;
    }

    @Override
    public boolean allowsOtherViewers() {
        return true;
    }

    void addViewer(Player p, boolean admin, boolean edit) {
        viewers.put(p.getUniqueId(), new Mode(admin, edit));
    }

    @Override
    public void render() {
        // Capture anything placed since the last sync before redrawing, so a redraw can never overwrite it.
        if (rendered) sync(clicker);
        rendered = true;
        VaultTally t = data.tally();
        Vars vars = Vars.of("owner", ownerName, "page", page + 1, "pages", pages(data.size), "unique", t.unique(), "total", t.total(),
                "size", data.size, "used", data.used());
        MenuLayout layout = layout();
        List<Integer> taken = new ArrayList<>();
        for (int slot = 0; slot < storage.length; slot++) {
            if (storage[slot] >= 0) {
                ItemStack item = data.items[storage[slot]];
                inventory.setItem(slot, item == null ? null : item.clone());
                taken.add(slot);
            }
        }
        if (paged) {
            if (page > 0 && layout.hasButton("previous-page")) {
                int s = layout.slot("previous-page", 45);
                set(s, layout.buttonItem(plugin, "previous-page", viewer, vars), c -> turn(-1));
                taken.add(s);
            }
            if (page < pages(data.size) - 1 && layout.hasButton("next-page")) {
                int s = layout.slot("next-page", 53);
                set(s, layout.buttonItem(plugin, "next-page", viewer, vars), c -> turn(1));
                taken.add(s);
            }
            if (layout.hasButton("info")) {
                int s = layout.slot("info", 49);
                set(s, layout.buttonItem(plugin, "info", viewer, vars), null);
                taken.add(s);
            }
        }
        // Slots past the vault's size are locked.
        ItemStack locked = layout.cfg().isSection("locked-slot")
                ? com.gradychastain.headcount.item.ItemSpec.parse(layout.cfg().getSection("locked-slot").raw(), Material.BLACK_STAINED_GLASS_PANE).build(plugin, viewer, vars)
                : null;
        for (int slot = 0; slot < storage.length; slot++) {
            if (storage[slot] < 0 && !taken.contains(slot)) {
                if (paged && slot >= PAGED_SLOTS) continue;   // the button row is the filler's
                set(slot, locked == null ? new ItemStack(Material.BLACK_STAINED_GLASS_PANE) : locked.clone(), null);
                taken.add(slot);
            }
        }
        if (paged) layout.fill(this, plugin, viewer, vars, taken);
    }

    private void turn(int delta) {
        // Opening the other page closes this view for the clicker (syncing it first).
        Player p = clicker == null ? viewer : clicker;
        Mode m = viewers.get(p.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) manager.open(p, data.owner, page + delta, m != null && m.admin, m == null || m.edit);
        });
    }

    // ---------------------------------------------------------------- rules

    private boolean canMove(Player p, Mode mode) {
        if (!mode.edit) {
            plugin.lang().send(p, "vault.view-only");
            return false;
        }
        if (mode.admin) return true;
        if (!p.getUniqueId().equals(data.owner)) return false;
        if (plugin.cycles().isProcessing()) {
            plugin.lang().send(p, "cycle.processing");
            return false;
        }
        if (plugin.actions().isDenied(p, "vault", null)) return false;
        return !manager.moveGate().check(p, Vars.of("owner", ownerName));
    }

    /** Whether an item may be placed into a vault slot right now; tells the player why not. */
    private boolean acceptable(Player p, ItemStack item) {
        if (Items.isEmpty(item)) return true;
        HeadData h = plugin.heads().read(item);
        if (h == null) {
            plugin.lang().send(p, "vault.not-economy-head");
            return false;
        }
        if (!plugin.heads().isCurrent(h)) {
            plugin.lang().send(p, "vault.expired-head", Vars.of("cycle", h.cycle()));
            return false;
        }
        if (item.getAmount() > 1) {
            reportDuplicate(p, h, "stacked-copies");
            plugin.lang().send(p, "vault.duplicate-head");
            return false;
        }
        UUID holder = manager.vaultHolding(h.id());
        if (holder != null && !plugin.configs().main().getString("vault.duplicate-insert", "reject").equalsIgnoreCase("allow")) {
            reportDuplicate(p, h, "already-in-vault-of:" + holder);
            plugin.lang().send(p, "vault.duplicate-head");
            return false;
        }
        return true;
    }

    private void reportDuplicate(Player p, HeadData h, String kind) {
        plugin.audit().log(AuditType.DUPLICATE, "kind", kind, "head", h.id(), "owner", h.ownerName(), "cycle", h.cycle(),
                "player", p.getName() + "(" + p.getUniqueId() + ")", "vault", ownerName + "(" + data.owner + ")");
        plugin.dupes().alert(kind, p.getName(), h.id().toString());
    }

    // ---------------------------------------------------------------- events

    @Override
    public boolean handleClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return true;
        }
        Mode mode = viewers.get(p.getUniqueId());
        if (mode == null) {
            e.setCancelled(true);
            return true;
        }
        clicker = p;
        sync(p);
        int raw = e.getRawSlot();
        int topSize = inventory.getSize();
        InventoryAction action = e.getAction();
        if (raw >= 0 && raw < topSize) {
            int global = storage[raw];
            if (global < 0) {
                e.setCancelled(true);
                if (isButtonClick(e.getClick())) {
                    var b = buttons.get(raw);
                    if (b != null && plugin.guis().clickAllowed(p)) {
                        clicker = p;
                        b.accept(e.getClick());
                    }
                }
                return true;
            }
            switch (action) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE, MOVE_TO_OTHER_INVENTORY -> {
                    if (!canMove(p, mode)) return deny(e);
                    return allow(p);
                }
                case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> {
                    if (!canMove(p, mode) || !acceptable(p, e.getCursor())) return deny(e);
                    return allow(p);
                }
                case HOTBAR_SWAP -> {
                    ItemStack incoming = e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand()
                            : e.getHotbarButton() >= 0 ? p.getInventory().getItem(e.getHotbarButton()) : null;
                    if (!canMove(p, mode) || !acceptable(p, incoming)) return deny(e);
                    return allow(p);
                }
                default -> {
                    // DROP_*_SLOT, CLONE_STACK, COLLECT_TO_CURSOR, bundle actions, HOTBAR_MOVE_AND_READD, NOTHING, UNKNOWN
                    if (action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT) {
                        if (plugin.configs().main().getBool("vault.allow-drop-key", false) && canMove(p, mode)) return allow(p);
                    }
                    return deny(e);
                }
            }
        }
        if (raw < 0) return false; // clicking outside the window drops the cursor, which belongs to the player
        // The player's own inventory.
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (Items.isEmpty(item) || !canMove(p, mode) || !acceptable(p, item)) return true;
            int target = -1;
            for (int s = 0; s < storage.length; s++) {
                if (storage[s] >= 0 && Items.isEmpty(inventory.getItem(s))) {
                    target = s;
                    break;
                }
            }
            if (target < 0) {
                plugin.lang().send(p, "vault.full");
                return true;
            }
            ItemStack one = item.clone();
            one.setAmount(1);
            inventory.setItem(target, one);
            item.setAmount(item.getAmount() - 1);
            e.setCurrentItem(item.getAmount() <= 0 ? null : item);
            sync(p);
            return true;
        }
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            ItemStack cursor = e.getCursor();
            if (cursor.getType() == Material.PLAYER_HEAD) return deny(e);
        }
        return false;
    }

    private boolean deny(InventoryClickEvent e) {
        e.setCancelled(true);
        return true;
    }

    private boolean allow(Player p) {
        queueSync(p);
        return false;
    }

    @Override
    public boolean handleDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return true;
        }
        int topSize = inventory.getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (!touchesTop) return false;
        Mode mode = viewers.get(p.getUniqueId());
        if (mode == null) {
            e.setCancelled(true);
            return true;
        }
        sync(p);
        for (int s : e.getRawSlots()) {
            if (s < topSize && storage[s] < 0) {
                e.setCancelled(true);
                return true;
            }
        }
        if (!canMove(p, mode) || !acceptable(p, e.getOldCursor())) {
            e.setCancelled(true);
            return true;
        }
        queueSync(p);
        return false;
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        HumanEntity who = e.getPlayer();
        sync(who instanceof Player p ? p : null);
        viewers.remove(who.getUniqueId());
        boolean anyLeft = inventory.getViewers().stream().anyMatch(v -> !v.getUniqueId().equals(who.getUniqueId()));
        if (!anyLeft) manager.viewClosed(this);
    }

    @Override
    public void forceClose() {
        if (closing) return;
        closing = true;
        sync(null);
        for (HumanEntity h : new ArrayList<>(inventory.getViewers())) h.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        manager.viewClosed(this);
    }

    private void queueSync(Player actor) {
        if (syncQueued) return;
        syncQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            syncQueued = false;
            sync(actor);
        });
    }

    /** Copies what is in the slots into the vault data; anything that should not be there goes back to the actor. */
    void sync(Player actor) {
        if (!rendered) return;
        boolean changed = false;
        Mode mode = actor == null ? null : viewers.get(actor.getUniqueId());
        boolean admin = mode != null && mode.admin;
        String actorName = actor == null ? "system" : actor.getName();
        for (int slot = 0; slot < storage.length; slot++) {
            int g = storage[slot];
            if (g < 0) continue;
            ItemStack inv = inventory.getItem(slot);
            HeadData current = data.heads[g];
            if (Items.isEmpty(inv)) {
                if (current != null) {
                    manager.setSlot(data, g, null, null, actorName, admin);
                    changed = true;
                }
                continue;
            }
            HeadData h = plugin.heads().read(inv);
            if (h != null && current != null && h.id().equals(current.id()) && inv.getAmount() == 1) continue;
            boolean valid = h != null && plugin.heads().isCurrent(h) && inv.getAmount() == 1;
            if (valid) {
                UUID holder = manager.vaultHolding(h.id());
                boolean elsewhere = holder != null && !(holder.equals(data.owner) && data.indexOf(h.id()) == g);
                if (elsewhere && !plugin.configs().main().getString("vault.duplicate-insert", "reject").equalsIgnoreCase("allow")) valid = false;
            }
            if (!valid) {
                // Never put the previous head back: it left this slot (it is on someone's cursor or in their
                // inventory), so restoring it would duplicate it. Record it as removed and eject the invalid item.
                inventory.setItem(slot, null);
                if (current != null) {
                    manager.setSlot(data, g, null, null, actorName, admin);
                    changed = true;
                }
                Player back = actor != null ? actor : plugin.getServer().getPlayer(data.owner);
                plugin.audit().log(AuditType.VAULT_REJECT, "vault", ownerName + "(" + data.owner + ")", "item", Items.describe(inv),
                        "head", h == null ? null : h.id(), "returned-to", back == null ? "owner-queue" : back.getName());
                if (back != null) Items.giveOrDrop(back, inv);
                else plugin.deliveries().give(data.owner, List.of(inv), "vault-reject");
                continue;
            }
            manager.setSlot(data, g, inv, h, actorName, admin);
            changed = true;
        }
        if (changed) {
            if (actor != null) manager.scheduleSave(actor);
            Player owner = plugin.getServer().getPlayer(data.owner);
            if (owner != null && owner != actor) manager.scheduleSave(owner);
            // Other pages and other viewers of this vault see the change.
            for (VaultGui other : new ArrayList<>(manager.views.values())) {
                if (other != this && other.data == data) other.render();
            }
        }
    }
}
