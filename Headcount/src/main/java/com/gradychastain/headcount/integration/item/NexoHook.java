package com.gradychastain.headcount.integration.item;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

public final class NexoHook implements ItemHook {
    @Override public String prefix() { return "nexo"; }

    @Override
    public ItemStack create(String id) {
        ItemBuilder builder = NexoItems.itemFromId(id);
        return builder == null ? null : builder.build();
    }

    @Override
    public String idOf(ItemStack item) {
        return NexoItems.idFromItem(item);
    }
}
