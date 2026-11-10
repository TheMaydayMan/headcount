package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A player joined the current cycle for the first time (or was online when it started). Fired on the main thread. */
public final class PlayerCycleJoinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final org.bukkit.entity.Player player;
    private final int cycleId;

    public PlayerCycleJoinEvent(org.bukkit.entity.Player player, int cycleId) {
        this.player = player;
        this.cycleId = cycleId;
    }

    public org.bukkit.entity.Player getPlayer() {
        return player;
    }

    public int getCycleId() {
        return cycleId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
