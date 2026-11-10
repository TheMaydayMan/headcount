package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.item.ItemSpec;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** give-item, take-item, equip, give-heads, loot, experience. */
final class ItemActions {
    private ItemActions() {}

    private static Cfg itemCfg(Cfg c) {
        return c.isSection("item") ? c.getSection("item") : c;
    }

    private static List<ItemStack> split(ItemStack item, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, item.getMaxStackSize());
        int left = Math.max(1, amount);
        while (left > 0) {
            ItemStack s = item.clone();
            s.setAmount(Math.min(max, left));
            left -= s.getAmount();
            out.add(s);
        }
        return out;
    }

    private static void removeTagged(Player p, NamespacedKey key, String id) {
        for (Inventory inv : List.of(p.getInventory(), p.getEnderChest())) {
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack s = contents[i];
                if (s != null && id.equals(s.getPersistentDataContainer().get(key, PersistentDataType.STRING))) inv.setItem(i, null);
            }
        }
        ItemStack cursor = p.getItemOnCursor();
        if (id.equals(cursor.getPersistentDataContainer().get(key, PersistentDataType.STRING))) p.setItemOnCursor(null);
    }

    static void register(ActionManager m, HeadcountPlugin plugin) {
        NamespacedKey actionKey = plugin.heads().keys().actionItem;
        NamespacedKey lockKey = plugin.heads().keys().lockedItem;

        m.registerType(Types.type("give-item", "material", c -> {
            ItemSpec spec = ItemSpec.parse(itemCfg(c).raw());
            int amount = Math.max(1, itemCfg(c).getInt("amount", c.getInt("amount", 1)));
            boolean ender = c.getString("to", "inventory").equalsIgnoreCase("enderchest");
            boolean takeBack = c.getBool("take-back", true);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    ItemStack item = spec.build(plugin, ctx.offlinePlayer(), Types.impl(ctx).vars());
                    if (ctx.instanceId() != null && takeBack) {
                        item.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, ctx.instanceId()));
                    }
                    List<ItemStack> stacks = split(item, amount);
                    Player p = ctx.player();
                    if (p == null) {
                        plugin.deliveries().give(ctx.playerId(), stacks, "action:" + ctx.source());
                        return true;
                    }
                    if (ender) {
                        List<ItemStack> left = Items.addAll(p.getEnderChest(), stacks);
                        if (!left.isEmpty()) {
                            Items.giveOrDrop(p, left);
                            plugin.lang().send(p, "items.inventory-full", "count", left.size());
                        }
                    } else {
                        int dropped = Items.giveOrDrop(p, stacks);
                        if (dropped > 0) plugin.lang().send(p, "items.inventory-full", "count", dropped);
                    }
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p != null) removeTagged(p, actionKey, ctx.instanceId());
                }

                @Override public boolean revertible() { return takeBack; }
                @Override public boolean requiresOnline() { return false; }
                @Override public String describe(ActionContext ctx) { return "item " + spec.describe().getFirst(); }
            };
        }));

        m.registerType(Types.type("take-item", "material", c -> {
            ItemSpec spec = ItemSpec.parse(itemCfg(c).raw());
            String amountRaw = c.getString("amount", "1");
            boolean all = amountRaw.equalsIgnoreCase("all");
            int amount = all ? Integer.MAX_VALUE : Math.max(1, c.getInt("amount", 1));
            String from = c.getString("from", "inventory").toLowerCase(Locale.ROOT);
            boolean giveBack = c.getBool("return", true);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    List<Inventory> invs = new ArrayList<>();
                    if (!from.equals("enderchest")) invs.add(p.getInventory());
                    if (from.equals("enderchest") || from.equals("both")) invs.add(p.getEnderChest());
                    int left = amount;
                    List<String> taken = new ArrayList<>();
                    for (Inventory inv : invs) {
                        ItemStack[] contents = inv.getContents();
                        for (int i = 0; i < contents.length && left > 0; i++) {
                            ItemStack s = contents[i];
                            if (s == null || !spec.matches(plugin, s)) continue;
                            // Economy heads are never taken by item matching; use give-heads/vault actions for heads.
                            if (plugin.heads().isEconomyHead(s)) continue;
                            int take = Math.min(left, s.getAmount());
                            ItemStack part = s.clone();
                            part.setAmount(take);
                            taken.add(Items.toBase64(part));
                            s.setAmount(s.getAmount() - take);
                            inv.setItem(i, s.getAmount() <= 0 ? null : s);
                            left -= take;
                        }
                    }
                    ctx.data().put("taken", taken);
                    return true;
                }

                @Override
                public void revert(ActionContext ctx) {
                    Object raw = ctx.data().get("taken");
                    if (!(raw instanceof List<?> list) || list.isEmpty()) return;
                    List<ItemStack> back = new ArrayList<>();
                    for (Object o : list) {
                        ItemStack i = Items.fromBase64(String.valueOf(o));
                        if (i != null) back.add(i);
                    }
                    plugin.deliveries().give(ctx.playerId(), back, "take-item-returned");
                }

                @Override public boolean revertible() { return giveBack; }
                @Override public String describe(ActionContext ctx) { return "items taken"; }
            };
        }));

        m.registerType(Types.type("equip", "material", c -> {
            ItemSpec spec = ItemSpec.parse(itemCfg(c).raw());
            String slotName = c.getString("slot", "head").toLowerCase(Locale.ROOT);
            EquipmentSlot slot = switch (slotName) {
                case "head", "helmet" -> EquipmentSlot.HEAD;
                case "chest", "chestplate" -> EquipmentSlot.CHEST;
                case "legs", "leggings" -> EquipmentSlot.LEGS;
                case "feet", "boots" -> EquipmentSlot.FEET;
                case "offhand", "off-hand" -> EquipmentSlot.OFF_HAND;
                default -> throw new IllegalArgumentException("equip slot must be head, chest, legs, feet or offhand");
            };
            boolean lock = c.getBool("lock", true);
            return new ActionHandler() {
                ItemStack build(ActionContext ctx) {
                    ItemStack item = spec.build(plugin, ctx.offlinePlayer(), Types.impl(ctx).vars());
                    item.setAmount(1);
                    item.editMeta(meta -> meta.getPersistentDataContainer().set(lockKey, PersistentDataType.STRING, ctx.instanceId() == null ? "instant" : ctx.instanceId()));
                    return item;
                }

                boolean holds(Player p, ActionContext ctx) {
                    ItemStack cur = p.getInventory().getItem(slot);
                    String id = ctx.instanceId() == null ? "instant" : ctx.instanceId();
                    return cur != null && id.equals(cur.getPersistentDataContainer().get(lockKey, PersistentDataType.STRING));
                }

                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    PlayerInventory inv = p.getInventory();
                    ItemStack previous = inv.getItem(slot);
                    if (!Items.isEmpty(previous) && !previous.getPersistentDataContainer().has(lockKey)) {
                        ctx.data().put("previous", Items.toBase64(previous));
                    }
                    inv.setItem(slot, build(ctx));
                    return true;
                }

                @Override
                public void maintain(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null || !lock || p.isDead()) return;
                    if (holds(p, ctx)) return;
                    removeTagged(p, lockKey, ctx.instanceId());
                    ItemStack cur = p.getInventory().getItem(slot);
                    if (!Items.isEmpty(cur)) {
                        p.getInventory().setItem(slot, null);
                        Items.giveOrDrop(p, cur);
                    }
                    p.getInventory().setItem(slot, build(ctx));
                }

                @Override
                public void revert(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return;
                    removeTagged(p, lockKey, ctx.instanceId());
                    Object prev = ctx.data().get("previous");
                    if (prev instanceof String s && !s.isEmpty()) {
                        ItemStack back = Items.fromBase64(s);
                        if (back != null) {
                            if (Items.isEmpty(p.getInventory().getItem(slot))) p.getInventory().setItem(slot, back);
                            else Items.giveOrDrop(p, back);
                        }
                    }
                }

                @Override public boolean revertible() { return true; }
                @Override public boolean stateful() { return lock; }
                @Override public String stackKey() { return slot.name(); }
                @Override public String describe(ActionContext ctx) { return "equipped " + spec.describe().getFirst() + (lock ? " (locked)" : ""); }
            };
        }));

        m.registerType(Types.type("give-heads", "amount", c -> {
            String ownerSpec = c.getString("owner", "self");
            int amount = Math.max(1, c.getInt("amount", 1));
            boolean next = c.getString("cycle", "current").equalsIgnoreCase("next");
            boolean toVault = c.getString("to", "inventory").equalsIgnoreCase("vault");
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    UUID to = ctx.playerId();
                    String ownerResolved = ctx.format(ownerSpec);
                    UUID owner;
                    if (ownerResolved.equalsIgnoreCase("self") || ownerResolved.isBlank()) owner = to;
                    else owner = plugin.players().resolve(ownerResolved);
                    if (owner == null) {
                        plugin.getLogger().warning("give-heads: unknown owner '" + ownerResolved + "'");
                        return false;
                    }
                    String ownerName = plugin.players().name(owner);
                    if (next) {
                        plugin.vaults().queueNextCycleHeads(to, owner, ownerName, amount, "action:" + ctx.source());
                        return true;
                    }
                    var cycle = plugin.cycles().current();
                    if (cycle == null) return false;
                    List<ItemStack> overflow = new ArrayList<>();
                    for (int i = 0; i < amount; i++) {
                        HeadData h = plugin.heads().newHead(owner, ownerName, cycle.id(), "action", to, "action:" + ctx.source());
                        ItemStack item = plugin.heads().createItem(h);
                        if (toVault && plugin.vaults().addHead(to, h, item, "action:" + ctx.source())) continue;
                        overflow.add(item);
                    }
                    if (!overflow.isEmpty()) plugin.deliveries().give(to, overflow, "give-heads");
                    return true;
                }

                @Override public boolean requiresOnline() { return false; }
            };
        }));

        m.registerType(Types.type("loot", "table", c -> {
            String table = c.getString("table", "minecraft:chests/simple_dungeon");
            NamespacedKey key = Types.key(table);
            float luck = (float) c.getDouble("luck", 0);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    LootTable lt = Bukkit.getLootTable(key);
                    if (lt == null) {
                        plugin.getLogger().warning("Loot table " + key + " does not exist.");
                        return false;
                    }
                    LootContext lc = new LootContext.Builder(p.getLocation()).lootedEntity(p).killer(p).luck(luck).build();
                    List<ItemStack> items = new ArrayList<>(lt.populateLoot(ThreadLocalRandom.current(), lc));
                    int dropped = Items.giveOrDrop(p, items);
                    if (dropped > 0) plugin.lang().send(p, "items.inventory-full", "count", dropped);
                    return true;
                }
            };
        }));

        m.registerType(Types.type("experience", "amount", c -> {
            int amount = c.getInt("amount", 0);
            boolean levels = c.getBool("levels", false);
            return new ActionHandler() {
                @Override
                public boolean apply(ActionContext ctx) {
                    Player p = ctx.player();
                    if (p == null) return false;
                    if (levels) {
                        p.setLevel(Math.max(0, p.getLevel() + amount));
                    } else if (amount >= 0) {
                        p.giveExp(amount);
                    } else {
                        int total = Math.max(0, p.calculateTotalExperiencePoints() + amount);
                        p.setExperienceLevelAndProgress(total);
                    }
                    return true;
                }
            };
        }));
    }
}
