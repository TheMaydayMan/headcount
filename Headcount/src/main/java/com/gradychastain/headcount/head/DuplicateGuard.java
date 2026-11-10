package com.gradychastain.headcount.head;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.text.Vars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flags duplicated economy head IDs. Creative mode can clone any item, so a head ID seen twice in one player's
 * inventory, ender chest or cursor, a stack of more than one identical head, or a head whose ID is already in some
 * vault, is written to the audit log as DUPLICATE and reported to admins. Vault inserts of an ID already stored in
 * a vault are rejected by the vault itself.
 */
public final class DuplicateGuard implements Listener {
    private final HeadcountPlugin plugin;
    private final Map<String, Long> lastAlert = new HashMap<>();
    private final Set<UUID> scanQueued = new HashSet<>();

    public DuplicateGuard(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    /** Tells online admins (headcount.admin.notify) about a duplicate, at most once a minute per head. */
    public void alert(String kind, String who, String headId) {
        if (!plugin.configs().main().getBool("duplicates.notify-admins", true)) return;
        String key = kind + ":" + headId;
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(key);
        if (last != null && now - last < 60_000) return;
        lastAlert.put(key, now);
        Vars v = Vars.of("kind", kind, "player", who == null ? "?" : who, "head", headId == null ? "?" : headId);
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("headcount.admin.notify")) plugin.lang().send(p, "duplicates.alert", v);
    }

    /** Scans a player's inventory, ender chest and cursor. Returns the number of slots holding a duplicated head. */
    public int scan(Player player, String trigger) {
        Map<UUID, Integer> seen = new HashMap<>();
        int dupes = 0;
        dupes += scanInventory(player, player.getInventory(), seen, trigger);
        dupes += scanInventory(player, player.getEnderChest(), seen, trigger);
        ItemStack cursor = player.getItemOnCursor();
        dupes += check(player, cursor, seen, trigger, null, -1);
        return dupes;
    }

    private int scanInventory(Player player, Inventory inv, Map<UUID, Integer> seen, String trigger) {
        int dupes = 0;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) dupes += check(player, contents[i], seen, trigger, inv, i);
        return dupes;
    }

    private int check(Player player, ItemStack item, Map<UUID, Integer> seen, String trigger, Inventory inv, int slot) {
        HeadData d = plugin.heads().read(item);
        if (d == null) return 0;
        int copies = item.getAmount();
        int before = seen.getOrDefault(d.id(), 0);
        seen.put(d.id(), before + copies);
        UUID vaultOwner = plugin.vaults().vaultHolding(d.id());
        boolean dup = copies > 1 || before > 0 || vaultOwner != null;
        if (!dup) return 0;
        plugin.audit().log(AuditType.DUPLICATE, "kind", "inventory", "head", d.id(), "owner", d.ownerName(), "cycle", d.cycle(),
                "player", player.getName() + "(" + player.getUniqueId() + ")", "copies", before + copies,
                "in-vault-of", vaultOwner, "trigger", trigger);
        alert("inventory", player.getName(), d.id().toString());
        if (plugin.configs().main().getBool("duplicates.remove-copies", false) && inv != null) {
            // Keep one copy only if no vault holds this ID and this is the first sighting.
            boolean keepOne = vaultOwner == null && before == 0;
            if (keepOne) {
                item.setAmount(1);
                inv.setItem(slot, item);
            } else {
                inv.setItem(slot, null);
            }
        }
        return 1;
    }

    /** Queues a scan for next tick (several triggers in one tick scan once). */
    public void queueScan(Player player, String trigger) {
        if (!scanQueued.add(player.getUniqueId())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            scanQueued.remove(player.getUniqueId());
            if (player.isOnline()) scan(player, trigger);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player p && plugin.heads().read(event.getCursor()) != null) queueScan(p, "creative-inventory");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p && plugin.configs().main().getBool("duplicates.scan-on-inventory-close", true)) queueScan(p, "inventory-close");
    }
}
