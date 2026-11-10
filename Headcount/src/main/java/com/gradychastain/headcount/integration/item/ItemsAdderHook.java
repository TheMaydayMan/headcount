package com.gradychastain.headcount.integration.item;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

public final class ItemsAdderHook implements ItemHook {
    @Override public String prefix() { return "itemsadder"; }

    @Override
    public ItemStack create(String id) {
        CustomStack stack = CustomStack.getInstance(id);
        return stack == null ? null : stack.getItemStack();
    }

    @Override
    public String idOf(ItemStack item) {
        CustomStack stack = CustomStack.byItemStack(item);
        return stack == null ? null : stack.getNamespacedID();
    }
}
