package com.gradychastain.headcount.integration.item;

import org.bukkit.inventory.ItemStack;

/** A custom item plugin. IDs are the plugin's own (e.g. {@code namespace:item} for ItemsAdder). */
public interface ItemHook {
    /** Prefix used in item definitions, e.g. {@code itemsadder} for {@code itemsadder:ns:item}. */
    String prefix();

    ItemStack create(String id);

    /** The custom ID of a stack, or null if it is not from this plugin. */
    String idOf(ItemStack item);
}
