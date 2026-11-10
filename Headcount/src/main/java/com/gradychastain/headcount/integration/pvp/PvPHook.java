package com.gradychastain.headcount.integration.pvp;

import org.bukkit.entity.Player;

/** A PvP or combat plugin. Every query returns null when the plugin cannot answer for this player. */
public interface PvPHook {
    String name();

    Boolean pvpEnabled(Player player);

    Boolean combatTagged(Player player);

    Boolean newbie(Player player);

    Boolean respawnProtected(Player player);

    Boolean override(Player player);

    /** Seconds of combat tag left, or null if unknown. */
    Long tagSecondsLeft(Player player);

    /** Sets the player's PvP toggle. Returns false if the plugin refused. */
    boolean setPvP(Player player, boolean enabled);

    /** Locks the player's PvP toggle to a state (null unlocks). Toggle attempts are cancelled while locked. */
    void lock(Player player, Boolean state);
}
