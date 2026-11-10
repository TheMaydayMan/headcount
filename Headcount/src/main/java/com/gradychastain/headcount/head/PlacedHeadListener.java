package com.gradychastain.headcount.head;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Items;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps economy data on placed heads. On placement the full item is stored in the skull block's PDC and in a
 * persistent index. Whenever the block becomes an item again, the vanilla drop is swapped for the stored item.
 * <p>
 * Known paths are handled directly (player break, explosions, pistons and fluids via {@link BlockBreakBlockEvent},
 * physics via {@link BlockDestroyEvent}, entities such as withers). Every removal also registers a pending drop; a
 * vanilla head item spawning in that block is converted ({@link ItemSpawnEvent}), and if nothing dropped within two
 * ticks the stored item is dropped anyway (configurable). The index also catches heads removed by paths Headcount
 * cannot see (commands, world editors) when their chunk next loads.
 */
public final class PlacedHeadListener implements Listener {
    private record Key(String world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        long chunk() {
            return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
        }
    }

    private record Placed(UUID id, byte[] item) {}

    private record Pending(ItemStack item, String cause, int tick, Location location) {}

    private final HeadcountPlugin plugin;
    private final HeadKeys keys;
    private final Map<Key, Placed> index = new HashMap<>();
    private final Map<String, Map<Long, Set<Key>>> byChunk = new HashMap<>();
    private final Map<Key, Pending> pending = new HashMap<>();
    private boolean fallbackScheduled;

    public PlacedHeadListener(HeadcountPlugin plugin) {
        this.plugin = plugin;
        this.keys = plugin.heads().keys();
    }

    private Cfg cfg() {
        return plugin.configs().main().getSection("heads.placed");
    }

    public void load() {
        index.clear();
        byChunk.clear();
        plugin.db().sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT world, x, y, z, head_id, item FROM placed_heads"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Key k = new Key(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                    try {
                        put(k, new Placed(UUID.fromString(rs.getString(5)), rs.getBytes(6)));
                    } catch (IllegalArgumentException ignored) {
                        // corrupt row; skipped
                    }
                }
            }
            return null;
        });
        // Validate chunks that are already loaded (startup or /reload).
        for (World w : plugin.getServer().getWorlds()) for (Chunk ch : w.getLoadedChunks()) validateChunk(w, ch.getX(), ch.getZ());
    }

    public int indexed() {
        return index.size();
    }

    private void put(Key k, Placed p) {
        index.put(k, p);
        byChunk.computeIfAbsent(k.world(), w -> new HashMap<>()).computeIfAbsent(k.chunk(), c -> new HashSet<>()).add(k);
    }

    private void unindex(Key k) {
        if (index.remove(k) == null) return;
        Map<Long, Set<Key>> w = byChunk.get(k.world());
        if (w != null) {
            Set<Key> s = w.get(k.chunk());
            if (s != null) {
                s.remove(k);
                if (s.isEmpty()) w.remove(k.chunk());
            }
        }
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM placed_heads WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, k.world());
                ps.setInt(2, k.x());
                ps.setInt(3, k.y());
                ps.setInt(4, k.z());
                ps.executeUpdate();
            }
        });
    }

    private static boolean isHead(Material m) {
        return m == Material.PLAYER_HEAD || m == Material.PLAYER_WALL_HEAD;
    }

    /** The stored economy item of a head block, or null if it is not a tracked economy head. */
    private ItemStack stored(Block block) {
        if (!isHead(block.getType())) return null;
        BlockState state = block.getState(false);
        if (!(state instanceof Skull skull)) return null;
        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        byte[] bytes = pdc.get(keys.placedItem, PersistentDataType.BYTE_ARRAY);
        String id = pdc.get(keys.placedId, PersistentDataType.STRING);
        if (bytes == null || id == null) return null;
        Placed tracked = index.get(Key.of(block));
        if (tracked == null || !tracked.id().toString().equals(id)) {
            // Economy data Headcount never saw placed here: a world-editor copy or copied block data.
            if (!cfg().getString("untracked-blocks", "strip").equalsIgnoreCase("allow")) return null;
        }
        return restore(bytes);
    }

    private ItemStack restore(byte[] bytes) {
        try {
            ItemStack item = Items.fromBytes(bytes);
            if (item == null) return null;
            item.setAmount(1);
            plugin.heads().refresh(item);
            return item;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not restore a placed economy head: " + e.getMessage());
            return null;
        }
    }

    /** Whether the head block carries Headcount data that is not tracked (used for duplicate reports). */
    private boolean hasUntrackedData(Block block) {
        if (!isHead(block.getType())) return false;
        if (!(block.getState(false) instanceof Skull skull)) return false;
        String id = skull.getPersistentDataContainer().get(keys.placedId, PersistentDataType.STRING);
        if (id == null) return false;
        Placed tracked = index.get(Key.of(block));
        return tracked == null || !tracked.id().toString().equals(id);
    }

    // ---------------------------------------------------------------- placement

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isHead(block.getType())) return;
        if (!(block.getState() instanceof Skull skull)) return;
        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        HeadData data = plugin.heads().read(event.getItemInHand());
        Key k = Key.of(block);
        if (data == null || !cfg().getBool("preserve", true)) {
            // A plain head (or copied block data from creative pick-block): make sure no economy data rides along.
            if (pdc.has(keys.placedItem) || pdc.has(keys.placedId)) {
                String id = pdc.get(keys.placedId, PersistentDataType.STRING);
                pdc.remove(keys.placedItem);
                pdc.remove(keys.placedId);
                skull.update(true, false);
                plugin.audit().log(AuditType.DUPLICATE, "kind", "copied-block-data-stripped", "head", id,
                        "player", event.getPlayer().getName() + "(" + event.getPlayer().getUniqueId() + ")", "location", loc(block));
                plugin.dupes().alert("copied-block-data", event.getPlayer().getName(), id);
            }
            return;
        }
        ItemStack one = event.getItemInHand().clone();
        one.setAmount(1);
        byte[] bytes = Items.toBytes(one);
        pdc.set(keys.placedItem, PersistentDataType.BYTE_ARRAY, bytes);
        pdc.set(keys.placedId, PersistentDataType.STRING, data.id().toString());
        skull.update(true, false);
        put(k, new Placed(data.id(), bytes));
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO placed_heads(world, x, y, z, head_id, item) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, k.world());
                ps.setInt(2, k.x());
                ps.setInt(3, k.y());
                ps.setInt(4, k.z());
                ps.setString(5, data.id().toString());
                ps.setBytes(6, bytes);
                ps.executeUpdate();
            }
        });
        plugin.audit().log(AuditType.HEAD_PLACE, "head", data.id(), "owner", data.ownerName(), "cycle", data.cycle(),
                "player", event.getPlayer().getName() + "(" + event.getPlayer().getUniqueId() + ")", "location", loc(block));
    }

    private static String loc(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    // ---------------------------------------------------------------- removal paths

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isHead(block.getType())) return;
        if (hasUntrackedData(block)) reportUntracked(block, event.getPlayer().getName());
        ItemStack item = stored(block);
        if (item == null) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            if (cfg().getString("creative-break", "destroy").equalsIgnoreCase("drop")) {
                markPending(block, item, "creative-break:" + event.getPlayer().getName());
            } else {
                HeadData d = plugin.heads().read(item);
                plugin.audit().log(AuditType.HEAD_DESTROYED, "head", d == null ? null : d.id(), "cause", "creative-break",
                        "player", event.getPlayer().getName() + "(" + event.getPlayer().getUniqueId() + ")", "location", loc(block));
                unindex(Key.of(block));
            }
            return;
        }
        markPending(block, item, "break:" + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDropItems(BlockDropItemEvent event) {
        BlockState state = event.getBlockState();
        if (!isHead(state.getType())) return;
        Key k = Key.of(event.getBlock());
        Pending p = pending.get(k);
        ItemStack item = p != null ? p.item() : storedFromState(state, k);
        if (item == null) return;
        for (Item drop : event.getItems()) {
            ItemStack s = drop.getItemStack();
            if (s.getType() == Material.PLAYER_HEAD && plugin.heads().read(s) == null) {
                drop.setItemStack(item);
                finish(k, item, p == null ? "break:" + event.getPlayer().getName() : p.cause());
                return;
            }
        }
    }

    private ItemStack storedFromState(BlockState state, Key k) {
        if (!(state instanceof Skull skull)) return null;
        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        byte[] bytes = pdc.get(keys.placedItem, PersistentDataType.BYTE_ARRAY);
        String id = pdc.get(keys.placedId, PersistentDataType.STRING);
        Placed tracked = index.get(k);
        if (bytes == null || id == null) return null;
        if ((tracked == null || !tracked.id().toString().equals(id)) && !cfg().getString("untracked-blocks", "strip").equalsIgnoreCase("allow")) return null;
        return restore(bytes);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) markIfHead(b, "explosion:" + event.getEntityType().name().toLowerCase(java.util.Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) markIfHead(b, "explosion:block");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) markIfHead(b, "piston");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) markIfHead(b, "piston");
    }

    /** Pistons and fluids destroying the head: swap the drop directly. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBreakBlock(BlockBreakBlockEvent event) {
        Block block = event.getBlock();
        Key k = Key.of(block);
        Pending p = pending.get(k);
        ItemStack item = p != null ? p.item() : stored(block);
        if (item == null) return;
        List<ItemStack> drops = event.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            ItemStack s = drops.get(i);
            if (s != null && s.getType() == Material.PLAYER_HEAD && plugin.heads().read(s) == null) {
                drops.set(i, item);
                finish(k, item, p == null ? "broken-by:" + event.getSource().getType().name().toLowerCase(java.util.Locale.ROOT) : p.cause());
                return;
            }
        }
        if (p == null) markPending(block, item, "broken-by:" + event.getSource().getType().name().toLowerCase(java.util.Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        markIfHead(event.getBlock(), "physics");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (isHead(event.getBlock().getType()) && !isHead(event.getTo())) {
            markIfHead(event.getBlock(), "entity:" + event.getEntityType().name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        markIfHead(event.getToBlock(), "fluid");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        markIfHead(event.getBlock(), "fire");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        markIfHead(event.getBlock(), "fade");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        for (BlockState s : event.getBlocks()) {
            Block b = s.getLocation().getBlock();
            if (isHead(b.getType()) && !isHead(s.getType())) markIfHead(b, "structure-grow");
        }
    }

    private void markIfHead(Block block, String cause) {
        if (!isHead(block.getType())) return;
        if (hasUntrackedData(block)) reportUntracked(block, cause);
        ItemStack item = stored(block);
        if (item != null) markPending(block, item, cause);
    }

    private void reportUntracked(Block block, String cause) {
        if (!(block.getState(false) instanceof Skull skull)) return;
        String id = skull.getPersistentDataContainer().get(keys.placedId, PersistentDataType.STRING);
        if (cfg().getString("untracked-blocks", "strip").equalsIgnoreCase("allow")) return;
        plugin.audit().log(AuditType.DUPLICATE, "kind", "untracked-head-block", "head", id, "cause", cause, "location", loc(block));
        plugin.dupes().alert("untracked-head-block", cause, id);
    }

    // ---------------------------------------------------------------- pending drops

    private void markPending(Block block, ItemStack item, String cause) {
        Key k = Key.of(block);
        if (pending.containsKey(k)) return;
        pending.put(k, new Pending(item, cause, plugin.getServer().getCurrentTick(), block.getLocation().add(0.5, 0.5, 0.5)));
        if (!fallbackScheduled) {
            fallbackScheduled = true;
            plugin.getServer().getScheduler().runTaskLater(plugin, this::runFallback, 2L);
        }
    }

    private void finish(Key k, ItemStack item, String cause) {
        pending.remove(k);
        unindex(k);
        HeadData d = plugin.heads().read(item);
        plugin.audit().log(AuditType.HEAD_BREAK, "head", d == null ? null : d.id(), "owner", d == null ? null : d.ownerName(),
                "cause", cause, "location", k.world() + "," + k.x() + "," + k.y() + "," + k.z());
    }

    /** Converts a vanilla head drop that spawned where an economy head was just removed. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (pending.isEmpty() && index.isEmpty()) return;
        ItemStack s = event.getEntity().getItemStack();
        if (s.getType() != Material.PLAYER_HEAD || plugin.heads().read(s) != null) return;
        Block b = event.getLocation().getBlock();
        Key k = Key.of(b);
        Pending p = pending.get(k);
        if (p != null) {
            ItemStack replacement = p.item().clone();
            replacement.setAmount(1);
            event.getEntity().setItemStack(replacement);
            finish(k, replacement, p.cause());
            return;
        }
        // Catch-all: the index says an economy head was placed here and the block is gone. Only a drop of the same
        // owner's head is converted, so an unrelated vanilla head dropped on that spot can never become an economy head.
        Placed placed = index.get(k);
        if (placed != null && !isHead(b.getType())) {
            ItemStack item = restore(placed.item());
            if (item != null && sameSkinOwner(s, item)) {
                event.getEntity().setItemStack(item);
                finish(k, item, "unknown");
            }
        }
    }

    private static boolean sameSkinOwner(ItemStack vanilla, ItemStack economy) {
        if (!(vanilla.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta a) || !(economy.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta b)) return false;
        var pa = a.getPlayerProfile();
        var pb = b.getPlayerProfile();
        if (pa == null || pb == null) return false;
        if (pa.getId() != null && pb.getId() != null) return pa.getId().equals(pb.getId());
        return pa.getName() != null && pa.getName().equalsIgnoreCase(pb.getName());
    }

    private void runFallback() {
        fallbackScheduled = false;
        boolean alwaysDrop = cfg().getBool("always-drop", true);
        int now = plugin.getServer().getCurrentTick();
        Iterator<Map.Entry<Key, Pending>> it = pending.entrySet().iterator();
        boolean reschedule = false;
        while (it.hasNext()) {
            Map.Entry<Key, Pending> e = it.next();
            Pending p = e.getValue();
            if (now - p.tick() < 2) {
                reschedule = true;
                continue;
            }
            it.remove();
            Block block = p.location().getBlock();
            if (isHead(block.getType()) && stored(block) != null) continue; // removal was cancelled after we saw it
            HeadData d = plugin.heads().read(p.item());
            if (alwaysDrop) {
                Items.dropNaturally(p.location(), p.item());
                unindex(e.getKey());
                plugin.audit().log(AuditType.HEAD_BREAK, "head", d == null ? null : d.id(), "cause", p.cause(), "drop", "fallback",
                        "location", e.getKey().world() + "," + e.getKey().x() + "," + e.getKey().y() + "," + e.getKey().z());
            } else {
                unindex(e.getKey());
                plugin.audit().log(AuditType.HEAD_DESTROYED, "head", d == null ? null : d.id(), "cause", p.cause(),
                        "location", e.getKey().world() + "," + e.getKey().x() + "," + e.getKey().y() + "," + e.getKey().z());
            }
        }
        if (reschedule && !fallbackScheduled) {
            fallbackScheduled = true;
            plugin.getServer().getScheduler().runTaskLater(plugin, this::runFallback, 1L);
        }
    }

    // ---------------------------------------------------------------- index validation

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Map<Long, Set<Key>> w = byChunk.get(event.getWorld().getName());
        if (w == null) return;
        long ck = ((long) event.getChunk().getX() << 32) ^ (event.getChunk().getZ() & 0xffffffffL);
        if (!w.containsKey(ck)) return;
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        World world = event.getWorld();
        plugin.getServer().getScheduler().runTask(plugin, () -> validateChunk(world, cx, cz));
    }

    private void validateChunk(World world, int cx, int cz) {
        Map<Long, Set<Key>> w = byChunk.get(world.getName());
        if (w == null) return;
        long ck = ((long) cx << 32) ^ (cz & 0xffffffffL);
        Set<Key> keysInChunk = w.get(ck);
        if (keysInChunk == null || !world.isChunkLoaded(cx, cz)) return;
        for (Key k : new ArrayList<>(keysInChunk)) {
            Block b = world.getBlockAt(k.x(), k.y(), k.z());
            Placed placed = index.get(k);
            boolean ok = false;
            if (placed != null && isHead(b.getType()) && b.getState(false) instanceof Skull skull) {
                String id = skull.getPersistentDataContainer().get(keys.placedId, PersistentDataType.STRING);
                ok = placed.id().toString().equals(id);
            }
            if (!ok && !pending.containsKey(k)) {
                unindex(k);
                plugin.audit().log(AuditType.HEAD_DESTROYED, "head", placed == null ? null : placed.id(), "cause", "removed-outside-gameplay",
                        "location", k.world() + "," + k.x() + "," + k.y() + "," + k.z());
            }
        }
    }
}
