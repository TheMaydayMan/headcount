package com.gradychastain.headcount.vault;

import com.gradychastain.headcount.head.HeadData;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The authoritative in-memory contents of one vault while it is loaded. */
public final class VaultData {
    final UUID owner;
    final int size;
    final ItemStack[] items;
    final HeadData[] heads;

    VaultData(UUID owner, int size) {
        this.owner = owner;
        this.size = Math.max(0, size);
        this.items = new ItemStack[this.size];
        this.heads = new HeadData[this.size];
    }

    public UUID owner() {
        return owner;
    }

    public int size() {
        return size;
    }

    public int firstEmpty() {
        for (int i = 0; i < size; i++) if (heads[i] == null) return i;
        return -1;
    }

    public int used() {
        int n = 0;
        for (HeadData h : heads) if (h != null) n++;
        return n;
    }

    public VaultTally tally() {
        Set<UUID> owners = new HashSet<>();
        int total = 0, own = 0;
        for (HeadData h : heads) {
            if (h == null) continue;
            total++;
            owners.add(h.owner());
            if (h.owner().equals(owner)) own++;
        }
        return new VaultTally(owners.size(), total, own);
    }

    public int indexOf(UUID headId) {
        for (int i = 0; i < size; i++) if (heads[i] != null && heads[i].id().equals(headId)) return i;
        return -1;
    }
}
