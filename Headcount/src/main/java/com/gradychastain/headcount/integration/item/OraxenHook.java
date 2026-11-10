package com.gradychastain.headcount.integration.item;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

public final class OraxenHook implements ItemHook {
    @Override public String prefix() { return "oraxen"; }

    @Override
    public ItemStack create(String id) {
        ItemBuilder builder = OraxenItems.getItemById(id);
        return builder == null ? null : builder.build();
    }

    @Override
    public String idOf(ItemStack item) {
        return OraxenItems.getIdByItem(item);
    }
}
