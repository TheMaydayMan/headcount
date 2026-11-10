package com.gradychastain.headcount.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Item helpers: giving with overflow drops, serialization, emptiness checks. */
public final class Items {
    private Items() {}

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getType().isAir() || item.getAmount() <= 0;
    }

    /**
     * Adds items to the player's inventory and drops anything that does not fit at their feet.
     *
     * @return the number of stacks that had to be dropped
     */
    public static int giveOrDrop(Player player, Collection<ItemStack> items) {
        int dropped = 0;
        for (ItemStack item : items) {
            if (isEmpty(item)) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
            for (ItemStack rest : left.values()) {
                drop(player.getLocation(), rest);
                dropped++;
            }
        }
        return dropped;
    }

    public static int giveOrDrop(Player player, ItemStack... items) {
        return giveOrDrop(player, List.of(items));
    }

    public static Item drop(Location location, ItemStack item) {
        if (location.getWorld() == null || isEmpty(item)) return null;
        return location.getWorld().dropItem(location, item.clone());
    }

    public static Item dropNaturally(Location location, ItemStack item) {
        if (location.getWorld() == null || isEmpty(item)) return null;
        return location.getWorld().dropItemNaturally(location, item.clone());
    }

    /** Adds to an inventory, returning whatever did not fit. */
    public static List<ItemStack> addAll(Inventory inventory, Collection<ItemStack> items) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack item : items) {
            if (isEmpty(item)) continue;
            left.addAll(inventory.addItem(item.clone()).values());
        }
        return left;
    }

    public static byte[] toBytes(ItemStack item) {
        return item.serializeAsBytes();
    }

    public static ItemStack fromBytes(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes);
    }

    public static String toBase64(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack fromBase64(String s) {
        return s == null || s.isEmpty() ? null : ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
    }

    /** Short human summary used in audit lines, e.g. {@code DIAMOND x3}. */
    public static String describe(ItemStack item) {
        if (isEmpty(item)) return "AIR";
        return item.getType().name() + " x" + item.getAmount();
    }
}
