package com.gradychastain.headcount.api.status;

import org.bukkit.entity.Player;

/** Tests named statuses; {@code !name} negates. */
@FunctionalInterface
public interface StatusResolver {
    boolean has(Player player, String status);
}
