package com.gradychastain.headcount.vault;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.cycle.Cycle;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.status.StatusGate;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Head vaults: a virtual inventory per player that holds only current-cycle economy heads. The in-memory
 * {@link VaultData} is authoritative while loaded and every change is written to the database as it happens.
 */
public final class VaultManager {
    private final HeadcountPlugin plugin;
    private final Map<UUID, VaultData> loaded = new HashMap<>();
    private final Map<UUID, UUID> headIndex = new ConcurrentHashMap<>();
    final Map<String, VaultGui> views = new HashMap<>();
    private StatusGate moveGate;
    private StatusGate openGate;

    public VaultManager(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    Cfg cfg() {
        return plugin.configs().main().getSection("vault");
    }

    public void reload() {
        List<String> statuses = cfg().getStringList("block-movement.statuses");
        moveGate = new StatusGate(plugin, "vault-move", statuses);
        openGate = new StatusGate(plugin, "vault-open", statuses);
    }

    public StatusGate moveGate() {
        return moveGate;
    }

    public StatusGate openGate() {
        return openGate;
    }

    /** Loads the head-ID index for the current cycle (startup). */
    public void loadIndex() {
        headIndex.clear();
        int cycle = plugin.cycles().currentId();
        if (cycle <= 0) return;
        plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT head_id, uuid FROM vault_items WHERE cycle=?")) {
                ps.setInt(1, cycle);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) headIndex.put(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)));
                }
            }
            return null;
        });
    }

    /** The vault owner holding this head ID right now, or null. */
    public UUID vaultHolding(UUID headId) {
        return headIndex.get(headId);
    }

    // ---------------------------------------------------------------- loading

    public VaultData data(UUID owner) {
        VaultData d = loaded.get(owner);
        if (d != null) return d;
        d = loadFromDb(owner);
        loaded.put(owner, d);
        return d;
    }

    private VaultData loadFromDb(UUID owner) {
        Cycle cycle = plugin.cycles().current();
        int fallbackSize = cycle == null ? cfg().getInt("size", 27) : cycle.vaultSize();
        // Vault rows only ever hold heads of the running cycle run (every start, restart and reset rewrites them).
        String token = cycle == null ? null : cycle.token();
        record Row(int slot, HeadData head, byte[] item) {}
        List<Row> rows = new ArrayList<>();
        int size = plugin.db().sync(c -> {
            int s = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT vault_size FROM players WHERE uuid=?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) s = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT slot, head_id, head_owner, head_owner_name, created_ms, source, cycle, item FROM vault_items WHERE uuid=?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        HeadData h = new HeadData(UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)), rs.getString(4),
                                rs.getInt(7), rs.getLong(5), rs.getString(6), token);
                        rows.add(new Row(rs.getInt(1), h, rs.getBytes(8)));
                    }
                }
            }
            return s;
        });
        if (size <= 0) size = fallbackSize;
        int maxSlot = rows.stream().mapToInt(Row::slot).max().orElse(-1);
        VaultData d = new VaultData(owner, Math.max(size, maxSlot + 1));
        for (Row r : rows) {
            if (r.slot() < 0) continue;
            ItemStack item = null;
            if (r.item() != null) {
                try {
                    item = Items.fromBytes(r.item());
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Vault item for head " + r.head().id() + " could not be read; regenerating it.");
                }
            }
            if (item == null) item = plugin.heads().createItem(r.head());
            else plugin.heads().refresh(item);
            d.items[r.slot()] = item;
            d.heads[r.slot()] = r.head();
        }
        return d;
    }

    public VaultTally tally(UUID owner) {
        VaultData d = loaded.get(owner);
        if (d != null) return d.tally();
        int cycle = plugin.cycles().currentId();
        return plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(DISTINCT head_owner), COUNT(*), SUM(CASE WHEN head_owner = uuid THEN 1 ELSE 0 END) FROM vault_items WHERE uuid=? AND cycle=?")) {
                ps.setString(1, owner.toString());
                ps.setInt(2, cycle);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new VaultTally(rs.getInt(1), rs.getInt(2), rs.getInt(3)) : VaultTally.EMPTY;
                }
            }
        });
    }

    /** Drops cached vaults nobody is viewing and whose owner is offline. */
    public void unloadIdle() {
        loaded.keySet().removeIf(owner -> Bukkit.getPlayer(owner) == null && views.values().stream().noneMatch(v -> v.data().owner.equals(owner)));
    }

    // ---------------------------------------------------------------- mutation

    /** Sets one slot (null to empty it), updating the index, the database and the audit log. */
    void setSlot(VaultData d, int slot, ItemStack item, HeadData head, String actor, boolean admin) {
        HeadData old = d.heads[slot];
        d.items[slot] = item == null ? null : item.clone();
        d.heads[slot] = head;
        if (old != null && (head == null || !old.id().equals(head.id()))) {
            if (d.owner.equals(headIndex.get(old.id()))) headIndex.remove(old.id());
        }
        if (head != null) headIndex.put(head.id(), d.owner);
        UUID owner = d.owner;
        byte[] bytes = item == null ? null : Items.toBytes(item);
        plugin.db().write(c -> {
            if (head == null) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM vault_items WHERE uuid=? AND slot=?")) {
                    ps.setString(1, owner.toString());
                    ps.setInt(2, slot);
                    ps.executeUpdate();
                }
            } else {
                upsert(c, owner, slot, head, bytes);
            }
        });
        boolean logMoves = plugin.configs().main().getBool("audit.log-vault-moves", true);
        if (logMoves || admin) {
            if (old != null && (head == null || !old.id().equals(head.id()))) {
                plugin.audit().log(admin ? AuditType.VAULT_ADMIN : AuditType.VAULT_OUT, "vault", plugin.players().name(owner) + "(" + owner + ")",
                        "slot", slot, "head", old.id(), "owner", old.ownerName(), "actor", actor, "direction", "out");
            }
            if (head != null && (old == null || !old.id().equals(head.id()))) {
                plugin.audit().log(admin ? AuditType.VAULT_ADMIN : AuditType.VAULT_IN, "vault", plugin.players().name(owner) + "(" + owner + ")",
                        "slot", slot, "head", head.id(), "owner", head.ownerName(), "actor", actor, "direction", "in");
            }
        }
    }

    private static void upsert(Connection c, UUID owner, int slot, HeadData head, byte[] bytes) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO vault_items(uuid, slot, head_id, head_owner, head_owner_name, created_ms, source, cycle, item) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, owner.toString());
            ps.setInt(2, slot);
            ps.setString(3, head.id().toString());
            ps.setString(4, head.owner().toString());
            ps.setString(5, head.ownerName());
            ps.setLong(6, head.created());
            ps.setString(7, head.source());
            ps.setInt(8, head.cycle());
            ps.setBytes(9, bytes);
            ps.executeUpdate();
        }
    }

    /** Puts a head into the first empty slot. @return false when the vault is full or the head is not current */
    public boolean addHead(UUID owner, HeadData head, ItemStack item, String actor) {
        if (!plugin.heads().isCurrent(head) || headIndex.containsKey(head.id())) return false;
        VaultData d = data(owner);
        int slot = d.firstEmpty();
        if (slot < 0) return false;
        setSlot(d, slot, item, head, actor, false);
        refreshViews(owner);
        return true;
    }

    /** Empties a vault (admin reset). */
    public int clear(UUID owner, String actor) {
        VaultData d = data(owner);
        int n = 0;
        for (int i = 0; i < d.size; i++) {
            if (d.heads[i] != null) {
                setSlot(d, i, null, null, actor, true);
                n++;
            }
        }
        refreshViews(owner);
        return n;
    }

    // ---------------------------------------------------------------- cycle setup

    private static int applyMod(int base, String mod) {
        if (mod == null || mod.isBlank()) return base;
        String m = mod.trim();
        try {
            if (m.startsWith("=")) return Integer.parseInt(m.substring(1).trim());
            if (m.startsWith("+")) return base + Integer.parseInt(m.substring(1).trim());
            if (m.startsWith("-")) return base - Integer.parseInt(m.substring(1).trim());
            return Integer.parseInt(m);
        } catch (NumberFormatException e) {
            return base;
        }
    }

    /** Combines two modifiers so several next-cycle actions add up: "+2" then "+3" is "+5"; "=10" then "+2" is "=12". */
    public static String combineMods(String old, String add) {
        if (old == null || old.isBlank()) return add;
        int o = applyMod(0, old), a = applyMod(0, add);
        boolean set = old.trim().startsWith("=") || add.trim().startsWith("=");
        if (add.trim().startsWith("=")) return add.trim();
        int total = o + a;
        return set ? "=" + total : (total >= 0 ? "+" + total : String.valueOf(total));
    }

    private int maxSize() {
        return Math.max(1, cfg().getInt("max-size", 270));
    }

    /**
     * Sets up a vault for a player who joins mid-cycle (or has never had one), exactly as it would have been at
     * the start of that cycle.
     */
    public void setupForCycle(UUID uuid, String name, Cycle cycle, PlayerData data) {
        int size = Math.min(maxSize(), Math.max(1, cycle.vaultSize()));
        int starting = Math.min(size, Math.max(0, cycle.startingHeads()));
        long now = System.currentTimeMillis();
        List<HeadData> heads = new ArrayList<>();
        for (int i = 0; i < starting; i++) heads.add(new HeadData(UUID.randomUUID(), uuid, name, cycle.id(), now, "cycle-start", cycle.token()));
        plugin.db().syncTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM vault_items WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            insertHeads(c, uuid, heads, 0);
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET setup_cycle=?, vault_size=? WHERE uuid=?")) {
                ps.setInt(1, cycle.id());
                ps.setInt(2, size);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
        });
        headIndex.values().removeIf(uuid::equals);
        for (HeadData h : heads) headIndex.put(h.id(), uuid);
        loaded.remove(uuid);
        if (data != null) {
            data.setupCycle = cycle.id();
            data.vaultSize = size;
        }
        for (HeadData h : heads) {
            plugin.audit().log(AuditType.HEAD_CREATE, "head", h.id(), "owner", name + "(" + uuid + ")", "cycle", cycle.id(),
                    "source", "cycle-start", "recipient", uuid, "note", "mid-cycle-setup");
        }
    }

    private static void insertHeads(Connection c, UUID vaultOwner, List<HeadData> heads, int firstSlot) throws SQLException {
        try (PreparedStatement vi = c.prepareStatement("INSERT OR REPLACE INTO vault_items(uuid, slot, head_id, head_owner, head_owner_name, created_ms, source, cycle, item) VALUES (?,?,?,?,?,?,?,?,NULL)");
             PreparedStatement hr = c.prepareStatement("INSERT OR IGNORE INTO heads(head_id, owner, cycle, source, created_ms, recipient) VALUES (?,?,?,?,?,?)")) {
            int slot = firstSlot;
            for (HeadData h : heads) {
                vi.setString(1, vaultOwner.toString());
                vi.setInt(2, slot++);
                vi.setString(3, h.id().toString());
                vi.setString(4, h.owner().toString());
                vi.setString(5, h.ownerName());
                vi.setLong(6, h.created());
                vi.setString(7, h.source());
                vi.setInt(8, h.cycle());
                vi.addBatch();
                hr.setString(1, h.id().toString());
                hr.setString(2, h.owner().toString());
                hr.setInt(3, h.cycle());
                hr.setString(4, h.source());
                hr.setLong(5, h.created());
                hr.setString(6, vaultOwner.toString());
                hr.addBatch();
            }
            vi.executeBatch();
            hr.executeBatch();
        }
    }

    /**
     * Fills every known player's vault for a new cycle (online or not; it only touches the virtual vault). Applies
     * next-cycle vault size and starting-head modifiers and queued next-cycle heads. Heads that do not fit are
     * delivered as items on the player's next join.
     *
     * @return number of vaults filled
     */
    public int fillAllForCycle(Cycle cycle) {
        Fill f = new Fill();
        plugin.db().syncTransaction(c -> fillRows(c, cycle, false, f));
        return afterFill(cycle, f);
    }

    /**
     * Refills every vault for a cycle restarted by /hca reset cycle, in the caller's transaction. The next-cycle
     * modifiers and queued heads that were used when this cycle first started are used again; ones waiting for the
     * next cycle stay waiting. Call {@link #afterFill(Cycle, Fill)} once the transaction has committed.
     */
    public Fill refillForRestart(Connection c, Cycle cycle) throws SQLException {
        Fill f = new Fill();
        fillRows(c, cycle, true, f);
        return f;
    }

    /** What a fill made, applied to memory and handed out after its transaction commits. */
    public static final class Fill {
        final Map<UUID, List<HeadData>> overflow = new LinkedHashMap<>();
        final Map<UUID, List<HeadData>> created = new LinkedHashMap<>();
        final Map<UUID, Integer> sizes = new HashMap<>();
        boolean restart;
    }

    private void fillRows(Connection c, Cycle cycle, boolean restart, Fill f) throws SQLException {
        long now = System.currentTimeMillis();
        int max = maxSize();
        Map<UUID, List<HeadData>> overflow = f.overflow;
        Map<UUID, List<HeadData>> created = f.created;
        Map<UUID, Integer> sizes = f.sizes;
        f.restart = restart;
        record P(UUID uuid, String name, String headsMod, String sizeMod) {}
        List<P> players = new ArrayList<>();
        // A first start uses the modifiers waiting for this cycle; a restart uses the ones its first start used.
        String mods = restart ? "cycle_heads_mod, cycle_size_mod" : "next_heads_mod, next_size_mod";
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, " + mods + " FROM players"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) players.add(new P(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getString(4)));
        }
        Map<UUID, List<HeadData>> pending = new HashMap<>();
        String which = restart ? "used_cycle=?" : "used_cycle IS NULL AND for_cycle<=?";
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, owner, owner_name, amount, source FROM pending_heads WHERE " + which)) {
            ps.setInt(1, cycle.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID to = UUID.fromString(rs.getString(1));
                    UUID owner = UUID.fromString(rs.getString(2));
                    for (int i = 0; i < Math.max(0, rs.getInt(4)); i++) {
                        pending.computeIfAbsent(to, k -> new ArrayList<>()).add(new HeadData(UUID.randomUUID(), owner, rs.getString(3),
                                cycle.id(), now, rs.getString(5) == null ? "action" : rs.getString(5), cycle.token()));
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM vault_items")) {
            ps.executeUpdate();
        }
        String set = restart ? "UPDATE players SET setup_cycle=?, vault_size=? WHERE uuid=?"
                : "UPDATE players SET setup_cycle=?, vault_size=?, cycle_heads_mod=next_heads_mod, cycle_size_mod=next_size_mod, next_heads_mod=NULL, next_size_mod=NULL WHERE uuid=?";
        try (PreparedStatement upd = c.prepareStatement(set)) {
            for (P p : players) {
                int size = Math.max(1, Math.min(max, applyMod(cycle.vaultSize(), p.sizeMod())));
                int wanted = Math.max(0, applyMod(cycle.startingHeads(), p.headsMod()));
                int starting = Math.min(size, wanted);
                // Extra starting heads from a next-cycle action (e.g. a shop purchase) that don't fit are
                // delivered as items rather than lost; a configured starting-heads above the size is still capped.
                int extraOut = Math.max(0, wanted - Math.max(size, cycle.startingHeads()));
                List<HeadData> heads = new ArrayList<>();
                for (int i = 0; i < starting; i++) heads.add(new HeadData(UUID.randomUUID(), p.uuid(), p.name(), cycle.id(), now, "cycle-start", cycle.token()));
                for (int i = 0; i < extraOut; i++) {
                    overflow.computeIfAbsent(p.uuid(), k -> new ArrayList<>()).add(new HeadData(UUID.randomUUID(), p.uuid(), p.name(), cycle.id(), now, "cycle-start", cycle.token()));
                }
                for (HeadData extra : pending.getOrDefault(p.uuid(), List.of())) {
                    if (heads.size() < size) heads.add(extra);
                    else overflow.computeIfAbsent(p.uuid(), k -> new ArrayList<>()).add(extra);
                }
                insertHeads(c, p.uuid(), heads, 0);
                created.put(p.uuid(), heads);
                sizes.put(p.uuid(), size);
                upd.setInt(1, cycle.id());
                upd.setInt(2, size);
                upd.setString(3, p.uuid().toString());
                upd.addBatch();
            }
            upd.executeBatch();
        }
        // Overflow heads still need registry rows.
        try (PreparedStatement hr = c.prepareStatement("INSERT OR IGNORE INTO heads(head_id, owner, cycle, source, created_ms, recipient) VALUES (?,?,?,?,?,?)")) {
            for (Map.Entry<UUID, List<HeadData>> e : overflow.entrySet()) {
                for (HeadData h : e.getValue()) {
                    hr.setString(1, h.id().toString());
                    hr.setString(2, h.owner().toString());
                    hr.setInt(3, h.cycle());
                    hr.setString(4, h.source());
                    hr.setLong(5, h.created());
                    hr.setString(6, e.getKey().toString());
                    hr.addBatch();
                }
            }
            hr.executeBatch();
        }
        if (!restart) {
            // Kept (marked with this cycle) so a restart of this cycle can use them again; older ones are done.
            try (PreparedStatement ps = c.prepareStatement("UPDATE pending_heads SET used_cycle=? WHERE used_cycle IS NULL AND for_cycle<=?")) {
                ps.setInt(1, cycle.id());
                ps.setInt(2, cycle.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pending_heads WHERE used_cycle<?")) {
                ps.setInt(1, cycle.id());
                ps.executeUpdate();
            }
        }
    }

    /** Applies a committed fill to memory, logs it and hands out heads that did not fit. @return vaults filled */
    public int afterFill(Cycle cycle, Fill f) {
        boolean logEach = plugin.configs().main().getBool("audit.log-each-cycle-start-head", true);
        Map<UUID, List<HeadData>> overflow = f.overflow;
        Map<UUID, List<HeadData>> created = f.created;
        Map<UUID, Integer> sizes = f.sizes;
        closeAllViews();
        loaded.clear();
        headIndex.clear();
        for (Map.Entry<UUID, List<HeadData>> e : created.entrySet()) {
            for (HeadData h : e.getValue()) {
                headIndex.put(h.id(), e.getKey());
                if (logEach) plugin.audit().log(AuditType.HEAD_CREATE, "head", h.id(), "owner", h.ownerName() + "(" + h.owner() + ")",
                        "cycle", cycle.id(), "source", h.source(), "recipient", e.getKey());
            }
        }
        if (!logEach) {
            int total = created.values().stream().mapToInt(List::size).sum();
            plugin.audit().log(AuditType.HEAD_CREATE, "cycle", cycle.id(), "source", "cycle-start", "count", total, "vaults", created.size());
        }
        for (Map.Entry<UUID, List<HeadData>> e : overflow.entrySet()) {
            List<ItemStack> items = new ArrayList<>();
            for (HeadData h : e.getValue()) {
                items.add(plugin.heads().createItem(h));
                plugin.audit().log(AuditType.HEAD_CREATE, "head", h.id(), "owner", h.ownerName() + "(" + h.owner() + ")", "cycle", cycle.id(),
                        "source", h.source(), "recipient", e.getKey(), "note", "vault-full-delivered-as-item");
            }
            plugin.deliveries().give(e.getKey(), items, "next-cycle-heads-overflow");
        }
        for (PlayerData d : plugin.players().onlineData()) {
            d.setupCycle = cycle.id();
            Integer s = sizes.get(d.uuid);
            if (s != null) d.vaultSize = s;
            if (!f.restart) {
                d.nextHeadsMod = null;
                d.nextSizeMod = null;
            }
        }
        return created.size();
    }

    /** Queues heads to be added to a player's vault at the start of the next cycle. */
    public void queueNextCycleHeads(UUID to, UUID owner, String ownerName, int amount, String source) {
        int next = plugin.cycles().currentId() + 1;
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pending_heads(uuid, owner, owner_name, amount, source, for_cycle) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, to.toString());
                ps.setString(2, owner.toString());
                ps.setString(3, ownerName);
                ps.setInt(4, amount);
                ps.setString(5, source);
                ps.setInt(6, next);
                ps.executeUpdate();
            }
        });
    }

    /** Stores a next-cycle starting-heads or vault-size modifier. */
    public void addNextCycleMod(UUID uuid, boolean size, String mod) {
        PlayerData d = plugin.players().loaded(uuid);
        String column = size ? "next_size_mod" : "next_heads_mod";
        String current;
        if (d != null) current = size ? d.nextSizeMod : d.nextHeadsMod;
        else current = plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + column + " FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
        String combined = combineMods(current, mod);
        if (d != null) {
            if (size) d.nextSizeMod = combined;
            else d.nextHeadsMod = combined;
        }
        plugin.playerStore().set(uuid, column, combined);
    }

    // ---------------------------------------------------------------- views

    /** Opens a vault page. {@code edit} is false for read-only admin views. */
    public void open(Player viewer, UUID owner, int page, boolean adminView, boolean edit) {
        if (plugin.cycles().current() == null) {
            plugin.lang().send(viewer, "cycle.not-started");
            return;
        }
        if (plugin.cycles().isProcessing()) {
            plugin.lang().send(viewer, "cycle.processing");
            return;
        }
        if (!adminView) {
            if (plugin.actions().isDenied(viewer, "vault", null)) return;
            if (cfg().getBool("block-movement.block-opening", false) && openGate.check(viewer, Vars.empty())) return;
        }
        VaultData d = data(owner);
        int pages = VaultGui.pages(d.size);
        int p = Math.max(0, Math.min(page, pages - 1));
        String key = owner + "#" + p;
        VaultGui gui = views.get(key);
        if (gui == null) {
            gui = new VaultGui(plugin, viewer, d, p, plugin.players().name(owner));
            views.put(key, gui);
        }
        gui.addViewer(viewer, adminView, edit);
        gui.render();
        viewer.openInventory(gui.getInventory());
    }

    void viewClosed(VaultGui gui) {
        views.values().remove(gui);
        unloadIdle();
    }

    void refreshViews(UUID owner) {
        for (VaultGui v : new ArrayList<>(views.values())) if (v.data().owner.equals(owner)) v.render();
    }

    /** Keeps the titles of open vaults current (called every second). */
    public void refreshTitles() {
        for (VaultGui v : new ArrayList<>(views.values())) v.refreshTitle();
    }

    /** Syncs every open vault view into the data (before a tally). */
    public void flushAll() {
        for (VaultGui v : new ArrayList<>(views.values())) v.sync(null);
    }

    public void closeAllViews() {
        for (VaultGui v : new ArrayList<>(views.values())) v.forceClose();
        views.clear();
    }

    /** After the cycle-end wipe: forget cached vaults and the index. */
    public void onCycleWiped() {
        closeAllViews();
        loaded.clear();
        headIndex.clear();
    }

    /** Saves the player's inventory to disk shortly after a transfer so a crash cannot roll back one side only. */
    void scheduleSave(Player p) {
        if (cfg().getBool("save-player-data-after-change", true)) plugin.players().scheduleSave(p);
    }
}
