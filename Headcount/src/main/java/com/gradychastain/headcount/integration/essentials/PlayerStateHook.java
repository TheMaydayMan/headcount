package com.gradychastain.headcount.integration.essentials;

import org.bukkit.entity.Player;

/** Player states for the player-state status source: afk, vanished, god, jailed, muted. Null when it can't tell. */
public interface PlayerStateHook {
    String name();

    Boolean check(Player player, String what);
}
